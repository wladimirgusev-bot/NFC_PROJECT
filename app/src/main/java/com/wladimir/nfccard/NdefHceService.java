package com.wladimir.nfccard;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class NdefHceService extends HostApduService {

    private static final String PREFS = "nfc_card";

    private static final byte[] OK = hex("9000");
    private static final byte[] NOT_FOUND = hex("6A82");
    private static final byte[] WRONG_PARAMS = hex("6B00");

    private static final byte[] NDEF_AID = hex("00A4040007D276000085010100");
    private static final byte[] SELECT_CC = hex("00A4000C02E103");
    private static final byte[] SELECT_NDEF = hex("00A4000C02E104");
    private static final byte[] CC_FILE = hex("000F20003B00340406E10400FF00FF");

    private byte[] ndefFile;
    private int selectedFile = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        ndefFile = buildNdefFile();
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("enabled", false)) {
            return NOT_FOUND;
        }

        if (commandApdu == null) {
            return WRONG_PARAMS;
        }

        if (equals(commandApdu, NDEF_AID)) {
            selectedFile = 0;
            ndefFile = buildNdefFile();
            return OK;
        }

        if (equals(commandApdu, SELECT_CC)) {
            selectedFile = 1;
            return OK;
        }

        if (equals(commandApdu, SELECT_NDEF)) {
            selectedFile = 2;
            ndefFile = buildNdefFile();
            return OK;
        }

        if (commandApdu.length >= 5
                && (commandApdu[0] & 0xFF) == 0x00
                && (commandApdu[1] & 0xFF) == 0xB0) {

            int offset = ((commandApdu[2] & 0xFF) << 8) | (commandApdu[3] & 0xFF);
            int le = commandApdu[4] & 0xFF;
            if (le == 0) le = 256;

            byte[] source = selectedFile == 1 ? CC_FILE :
                    (selectedFile == 2 ? ndefFile : null);

            if (source == null || offset > source.length) {
                return WRONG_PARAMS;
            }

            int end = Math.min(source.length, offset + le);
            return concat(Arrays.copyOfRange(source, offset, end), OK);
        }

        return NOT_FOUND;
    }

    @Override
    public void onDeactivated(int reason) {
        selectedFile = 0;
    }

    private byte[] buildNdefFile() {
        String name = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("name", "Wladimir Gusev");
        String job = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("job", "Lead Project Manager");
        String phone = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("phone", "+7 (926) 610-10-36");
        String email = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("email", "Vladimir.Gusev@rp.medical.canon");

        StringBuilder vcard = new StringBuilder();
        vcard.append("BEGIN:VCARD\r\n");
        vcard.append("VERSION:3.0\r\n");
        vcard.append("N:").append(escape(name)).append(";;;;\r\n");
        vcard.append("FN:").append(escape(name)).append("\r\n");

        if (!job.trim().isEmpty()) {
            vcard.append("TITLE:").append(escape(job)).append("\r\n");
        }
        if (!phone.trim().isEmpty()) {
            vcard.append("TEL;TYPE=CELL:").append(escape(phone)).append("\r\n");
        }
        if (!email.trim().isEmpty()) {
            vcard.append("EMAIL;TYPE=INTERNET:").append(escape(email)).append("\r\n");
        }

        vcard.append("END:VCARD\r\n");

        byte[] type = "text/vcard".getBytes(StandardCharsets.US_ASCII);
        byte[] payload = vcard.toString().getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream msg = new ByteArrayOutputStream();

        if (payload.length <= 255) {
            msg.write(0xD2);
            msg.write(type.length);
            msg.write(payload.length);
        } else {
            msg.write(0xC2);
            msg.write(type.length);
            msg.write((payload.length >>> 24) & 0xFF);
            msg.write((payload.length >>> 16) & 0xFF);
            msg.write((payload.length >>> 8) & 0xFF);
            msg.write(payload.length & 0xFF);
        }

        msg.write(type, 0, type.length);
        msg.write(payload, 0, payload.length);

        byte[] ndef = msg.toByteArray();

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write((ndef.length >>> 8) & 0xFF);
        file.write(ndef.length & 0xFF);
        file.write(ndef, 0, ndef.length);

        return file.toByteArray();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace(";", "\\;")
                .replace(",", "\\,");
    }

    private static boolean equals(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] hex(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) (
                    (Character.digit(s.charAt(i), 16) << 4)
                            + Character.digit(s.charAt(i + 1), 16)
            );
        }

        return data;
    }
}
