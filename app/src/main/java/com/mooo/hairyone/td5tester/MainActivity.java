package com.mooo.hairyone.td5tester;

import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static D2xxManager d2xx_manager = null;
    private FT_Device ft_device = null;

    TextView tvInfo;
    Button btConnect;
    Button btClear;

    byte[] response = new byte[TD5_Constants.BUFFER_SIZE];

    boolean connected = false;
    TD5_Requests td5_requests = null;

    private static final int LOG_MSG = 1;
    private static final int SET_CONNECTION_STATE = 2;

    private final Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case (LOG_MSG):
                    log_append((String) msg.obj);
                    break;
                case (SET_CONNECTION_STATE):
                    connected = (boolean) msg.obj;
                    break;
            }
        }
    };

    public void log_msg(String msg) {
        myHandler.sendMessage(Message.obtain(myHandler, LOG_MSG, msg));
    }

    public void set_connection_state(boolean connected) {
        myHandler.sendMessage(Message.obtain(myHandler, LOG_MSG, connected));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btConnect = (Button) findViewById(R.id.btConnect);
        btClear = (Button) findViewById(R.id.btClear);
        tvInfo = (TextView) findViewById(R.id.tvInfo);
        tvInfo.setMovementMethod(new ScrollingMovementMethod());
        tvInfo.setBackgroundColor(Color.parseColor("#FFFFA5"));

        btConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        openDevice();
                        fast_init();
                    }
                });
                thread.start();
            }
        });

        btClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log_clear();
            }
        });

        try {
            d2xx_manager = D2xxManager.getInstance(this);
        } catch (Exception ex) {
            log_msg(ex.getMessage());
        }
        td5_requests = new TD5_Requests();
    }

    void log_clear() {
        tvInfo.setText("");
    }

    void log_append(String text) {
        // SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        // SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        // Date now = new Date();
        // LoggingTextView.append(String.format("%s : %s\n", sdf.format(now), text));
        tvInfo.append(String.format("%s\n", text));
        Log.w("TD5Tester", text);
    }

    private void openDevice() {
        try {
            if (ft_device != null &&  ft_device.isOpen()) {
                log_msg("device already open");
                ft_device.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
                ft_device.restartInTask();
                return;
            }

            int device_count = 0;
            D2xxManager.FtDeviceInfoListNode[] device_list = null;
            device_count = d2xx_manager.createDeviceInfoList(this);
            device_list = new D2xxManager.FtDeviceInfoListNode[device_count];
            d2xx_manager.getDeviceInfoList(device_count, device_list);

            if (device_count <= 0) {
                log_msg("no ftdi devices detected");
                return;
            }
            for (D2xxManager.FtDeviceInfoListNode ft_device_info_list_node : device_list) {
                log_device_info(ft_device_info_list_node);
            }

            ft_device = d2xx_manager.openByIndex(this, 0);
            //ft_device.setLatencyTimer((byte) 16);
            //ft_device.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
            //ft_device.restartInTask();

        } catch (Exception ex) {
            log_msg(ex.getMessage());
        }
    }

    void seed_test() {
        // https://github.com/pajacobson/td5keygen
        // Seed request      | Seed response
        // 04 67 01 34 A5 45 | 04 27 02 54 D3 54
        //          ^^ ^^    |          ^^ ^^

        int seed = 0x34 << 8 | 0xA5;
        int key = generate_key(seed);

        log_msg(String.format("\nseed=%d, key=%s",seed, key));
    }

    public void log_device_info(D2xxManager.FtDeviceInfoListNode ft_device_info_list_node) {
        log_msg(String.format("serial_number=%s", ft_device_info_list_node.serialNumber == null ? "null" : ft_device_info_list_node.serialNumber));
        log_msg(String.format("description=%s", ft_device_info_list_node.description == null ? "null" : ft_device_info_list_node.description));
        log_msg(String.format("location=%d", ft_device_info_list_node.location));
        log_msg(String.format("id=%s", ft_device_info_list_node.id));
        log_msg(String.format("type=%d", ft_device_info_list_node.type));
        log_msg(String.format("type_name=%s", get_device_type(ft_device_info_list_node.type)));
    }

    public void fast_init() {
        //Mask = 0x00  // all input
        //Mask = 0xFF  // all output
        //Mask = 0x0F  // upper nibble input,lower nibble output

        //#define PIN_TX  0x01  /* Orange wire on FTDI cable */
        //#define PIX_RX  0x02  /* Yellow */
        //#define PIN_RTS 0x04  /* Green */
        //#define PIN_CTS 0x08  /* Brown */
        //#define PIN_DTR 0x10
        //#define PIN_DSR 0x20
        //#define PIN_DCD 0x40
        //#define PIN_RI  0x80

        if (ft_device != null) {
            try {
                log_msg(String.format("setBaudRate=%b", ft_device.setBaudRate(10400)));
                log_msg(String.format("setDataCharacteristics=%b", ft_device.setDataCharacteristics(D2xxManager.FT_DATA_BITS_8, D2xxManager.FT_STOP_BITS_1, D2xxManager.FT_PARITY_NONE)));
                log_msg(String.format("setBitMode=%b", ft_device.setBitMode((byte) 0x01, D2xxManager.FT_BITMODE_ASYNC_BITBANG)));

                byte[] HI = new byte[]{(byte) 0x01};
                byte[] LO = new byte[]{(byte) 0x00};

                long current = System.currentTimeMillis();
                log_msg(String.format("HI=%d", ft_device.write(HI, 1))); Thread.sleep(350);
                log_msg(String.format("elapsed=%d", System.currentTimeMillis() - current)); current = System.currentTimeMillis();
                log_msg(String.format("LO=%d", ft_device.write(LO, 1))); Thread.sleep(30);
                log_msg(String.format("elapsed=%d", System.currentTimeMillis() - current)); current = System.currentTimeMillis();
                log_msg(String.format("HI=%d", ft_device.write(HI, 1))); Thread.sleep(100);
                log_msg(String.format("elapsed=%d", System.currentTimeMillis() - current)); current = System.currentTimeMillis();

                // flush any data from the device buffers
                log_msg(String.format("purge=%b", ft_device.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX))));
                log_msg(String.format("queued_bytes=%d", ft_device.getQueueStatus()));

                log_msg(String.format("setBitMode=%b", ft_device.setBitMode((byte) 0x00, D2xxManager.FT_BITMODE_RESET)));

                if (get_pid(TD5_Pids.Pid.INIT_FRAME) && get_pid(TD5_Pids.Pid.START_DIAGNOSTICS) && get_pid(TD5_Pids.Pid.REQUEST_SEED)) {
                    int seed = (short) (response[3] << 8 | response[4]);
                    int key = generate_key(seed);
                    td5_requests.request.get(TD5_Pids.Pid.KEY_RETURN).request[3] = (byte) (key >> 8);
                    td5_requests.request.get(TD5_Pids.Pid.KEY_RETURN).request[4] = (byte) (key & 0xFF);
                    connected = get_pid(TD5_Pids.Pid.KEY_RETURN);
                }

            } catch (Exception ex) {
                log_msg(ex.getMessage());
            }
        }

    }

    int generate_key(int seedin) {
        // we have to use an int because java doesn't do unsigned values so we use the lower 16 bits of an int
        int seed = seedin;
        int count = ((seed >> 0xC & 0x8) + (seed >> 0x5 & 0x4) + (seed >> 0x3 & 0x2) + (seed & 0x1)) + 1; // count == byte (0 .. 255)
        // Log.d(String.format("\ncount=%d", count));
        for (int idx = 0; idx < count; idx++) {
            int tap = ((seed >> 1) + (seed >> 2) + (seed >> 8) + (seed >> 9)) & 1; // tap byte (0 .. 1)
            int tmp = (seed >> 1 & 0xFFFF) | ( tap << 0xF); // short (0 .. 65535)
            if ( (seed >> 0x3 & 1) == 1 && (seed >> 0xD & 1) == 1) {
                seed = tmp & ~1;
            } else {
                seed = tmp | 1;
            }
            // Log.d(String.format("tap=%d, tmp=%d, a=%d, b=%d, seed=%d", tap, tmp, seed >> 0x03 & 1, seed >> 0x0d & 1, seed));
        }
        // Log.d(String.format("seed=%d, key=%d", seedin, seed));
        return seed;
    }

    String get_device_type(int device_type) {
        String result = "unknown device";
        switch (device_type) {
            case D2xxManager.FT_DEVICE_232B:        result = "FT232B";          break;
            case D2xxManager.FT_DEVICE_8U232AM:     result = "FT8U232AM";       break;
            case D2xxManager.FT_DEVICE_UNKNOWN:     result = "Unknown";         break;
            case D2xxManager.FT_DEVICE_2232:        result = "FT2232";          break;
            case D2xxManager.FT_DEVICE_232R:        result = "FT232R";          break;
            case D2xxManager.FT_DEVICE_2232H:       result = "FT2232H";         break;
            case D2xxManager.FT_DEVICE_4232H:       result = "FT4232H";         break;
            case D2xxManager.FT_DEVICE_232H:        result = "FT232H";          break;
            case D2xxManager.FT_DEVICE_X_SERIES:    result = "FTDI X_SERIES";   break;
            default:                                result = "FT232B";          break;
        }
        return result;
    }

    boolean get_pid(TD5_Pids.Pid pid) {
        boolean result = false;
        send(pid);
        int len = readResponse(pid);
        if (len > 1) {
            byte cs1 = response[len - 1];
            byte cs2 = checksum(response, len - 1);
            if (cs1 == cs2) {
                if (response[1] != 0x7F) {
                    result = true;
                }
            }
        }
        return result;
    }

    void send(TD5_Pids.Pid pid) {
        int len     = td5_requests.request.get(pid).request_len;
        byte[] data = td5_requests.request.get(pid).request;
        String name = td5_requests.request.get(pid).name;

        data[len - 1] = checksum(data, len - 1);
        log_msg(name);
        log_data(data, len, true);
        ft_device.write(data, len);
    }

    byte checksum(byte[] data, int len) {
        byte crc = 0;
        for (int i = 0; i < len; i++) {
            crc = (byte) (crc + data[i]);
        }
        return crc;
    }

    static String byte_array_to_hex(byte[] data, int len){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++){
            sb.append(String.format("%02X ", data[i] & 0xFF));
        }
        return sb.toString();
    }

    int readResponse(TD5_Pids.Pid pid) {
        // When waiting for a request do we know how many characters to expect ?
        // The response could be: garbage, a negative response, or shorter than we expected
        // A valid response cannot be shorter than two bytes, and until we get the checksum
        // we don't know if the length byte is correct.  If the response if garbage we need
        // to empty the receive buffer anyway to get back in sync.

        // REQUESTING INIT FRAME
        // >> 81 13 F7 81 0C
        // << FF FF 03 C1 57 8F AA
        // REQUESTING START DIAGNOSTICS
        // >> 02 10 A0 B2
        // << FF FF 01 50 51
        // REQUESTING REQUEST SEED
        // >> 02 27 01 2A
        // << FF FF FF FF 04 67 01 52 25 E3
        // REQUESTING KEY RETURN
        // >> 04 27 02 14 89 CA
        // << FF FF FF FF 02 67 02 6B

        log_msg(String.format("queued_bytes=%d", ft_device.getQueueStatus()));
        int len = ft_device.read(response, TD5_Constants.BUFFER_SIZE, TD5_Constants.READ_RESPONSE_TIMEOUT);
        log_data(response, len, false);
        return len;
    }

    void log_data(byte[] data, int len, boolean is_tx) {
        log_msg(String.format("%s %s", is_tx ? ">>" : "<<", byte_array_to_hex(data, len)));
    }

    private void closeDevice() {
        if(ft_device != null && ft_device.isOpen()) {
            try {
                ft_device.close();
            } catch (Exception ex) {
                log_msg(ex.getMessage());
            }
        }
    }

    public void onDestroy() {
        super.onDestroy();
        closeDevice();
    }
}
