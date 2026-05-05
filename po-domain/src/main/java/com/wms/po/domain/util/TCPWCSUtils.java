package com.wms.po.domain.util;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * TCP/WCS Communication Utilities.
 *
 * Replaces: FN-031 - TCP/WCS functions (20 functions)
 * - fnc_TCP_Connect / fnc_TCP_Disconnect
 * - fnc_TCP_Send / fnc_TCP_Receive
 * - fnc_WCS_BuildMessage / fnc_WCS_ParseMessage
 * - fnc_WCS_SendCommand / fnc_WCS_ReceiveResponse
 * - fnc_WCS_FormatPickCommand / fnc_WCS_FormatPutawayCommand
 * - fnc_WCS_BuildASRS_Request / fnc_WCS_ParseASRS_Response
 * - fnc_WCS_BuildConveyor_Request
 * - fnc_TCP_CalculateChecksum / fnc_TCP_ValidateChecksum
 * - fnc_TCP_EscapeMessage / fnc_TCP_UnescapeMessage
 * - fnc_WCS_GetStatus / fnc_WCS_Heartbeat
 * - fnc_WCS_AcknowledgeMessage
 */
@Slf4j
public final class TCPWCSUtils {

    private TCPWCSUtils() {}

    // Message delimiters
    public static final String STX = "\u0002";  // Start of text
    public static final String ETX = "\u0003";  // End of text
    public static final String ACK = "\u0006";  // Acknowledge
    public static final String NAK = "\u0015";  // Negative acknowledge
    public static final String FIELD_DELIMITER = "|";
    public static final String RECORD_DELIMITER = "~";

    // Default timeout (ms)
    public static final int DEFAULT_TIMEOUT = 30000;
    public static final int DEFAULT_READ_TIMEOUT = 10000;

    // ═══════════════════════════════════════════════════════════════════════
    // TCP Connection
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Create TCP connection.
     * Replaces: fnc_TCP_Connect
     */
    public static TCPConnection connect(String host, int port, int timeoutMs) throws IOException {
        log.debug("Connecting to {}:{}", host, port);
        Socket socket = new Socket(host, port);
        socket.setSoTimeout(timeoutMs > 0 ? timeoutMs : DEFAULT_READ_TIMEOUT);
        return new TCPConnection(socket);
    }

    public static TCPConnection connect(String host, int port) throws IOException {
        return connect(host, port, DEFAULT_TIMEOUT);
    }

    /**
     * TCP Connection wrapper.
     */
    public static class TCPConnection implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;

        public TCPConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        /**
         * Send message.
         * Replaces: fnc_TCP_Send
         */
        public void send(String message) {
            log.debug("TCP Send: {}", message);
            writer.print(message);
            writer.flush();
        }

        /**
         * Receive message.
         * Replaces: fnc_TCP_Receive
         */
        public String receive() throws IOException {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            try {
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                    if (sb.toString().contains(ETX)) {
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                log.debug("Read timeout");
            }
            String response = sb.toString();
            log.debug("TCP Receive: {}", response);
            return response;
        }

        /**
         * Send and receive with timeout.
         */
        public String sendAndReceive(String message, int timeoutMs) throws IOException {
            socket.setSoTimeout(timeoutMs);
            send(message);
            return receive();
        }

        public boolean isConnected() {
            return socket != null && socket.isConnected() && !socket.isClosed();
        }

        @Override
        public void close() {
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                log.warn("Error closing TCP connection: {}", e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WCS Message Building
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build WCS message with header.
     * Replaces: fnc_WCS_BuildMessage
     */
    public static String buildWCSMessage(String messageType, String... fields) {
        StringBuilder sb = new StringBuilder();
        sb.append(STX);
        sb.append(messageType);
        for (String field : fields) {
            sb.append(FIELD_DELIMITER);
            sb.append(field != null ? field : "");
        }
        String body = sb.toString();
        sb.append(FIELD_DELIMITER);
        sb.append(calculateChecksum(body));
        sb.append(ETX);
        return sb.toString();
    }

    /**
     * Parse WCS message.
     * Replaces: fnc_WCS_ParseMessage
     */
    public static WCSMessage parseWCSMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        // Remove STX/ETX
        String content = message
            .replace(STX, "")
            .replace(ETX, "");

        String[] parts = content.split("\\" + FIELD_DELIMITER);
        if (parts.length < 1) {
            return null;
        }

        WCSMessage wcsMessage = new WCSMessage();
        wcsMessage.messageType = parts[0];
        wcsMessage.fields = new HashMap<>();

        for (int i = 1; i < parts.length - 1; i++) {
            wcsMessage.fields.put("field" + i, parts[i]);
        }

        // Last field is checksum
        if (parts.length > 1) {
            wcsMessage.checksum = parts[parts.length - 1];
        }

        return wcsMessage;
    }

    public static class WCSMessage {
        public String messageType;
        public Map<String, String> fields;
        public String checksum;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WCS Commands
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build pick command for WCS.
     * Replaces: fnc_WCS_FormatPickCommand
     */
    public static String buildPickCommand(String taskId, String location, String sku,
                                           int quantity, String lpn, String userId) {
        return buildWCSMessage("PICK",
            taskId,
            location,
            sku,
            String.valueOf(quantity),
            lpn,
            userId,
            timestamp()
        );
    }

    /**
     * Build putaway command for WCS.
     * Replaces: fnc_WCS_FormatPutawayCommand
     */
    public static String buildPutawayCommand(String taskId, String fromLoc, String toLoc,
                                              String sku, int quantity, String lpn, String userId) {
        return buildWCSMessage("PUTAWAY",
            taskId,
            fromLoc,
            toLoc,
            sku,
            String.valueOf(quantity),
            lpn,
            userId,
            timestamp()
        );
    }

    /**
     * Build ASRS retrieval request.
     * Replaces: fnc_WCS_BuildASRS_Request
     */
    public static String buildASRSRetrievalRequest(String location, String sku,
                                                    int quantity, String destination) {
        return buildWCSMessage("ASRS_RETRIEVE",
            location,
            sku,
            String.valueOf(quantity),
            destination,
            timestamp()
        );
    }

    /**
     * Build ASRS storage request.
     */
    public static String buildASRSStorageRequest(String lpn, String sku,
                                                  int quantity, String source) {
        return buildWCSMessage("ASRS_STORE",
            lpn,
            sku,
            String.valueOf(quantity),
            source,
            timestamp()
        );
    }

    /**
     * Build conveyor request.
     * Replaces: fnc_WCS_BuildConveyor_Request
     */
    public static String buildConveyorRequest(String lpn, String source,
                                               String destination, String priority) {
        return buildWCSMessage("CONVEYOR",
            lpn,
            source,
            destination,
            priority,
            timestamp()
        );
    }

    /**
     * Build heartbeat/status request.
     * Replaces: fnc_WCS_Heartbeat
     */
    public static String buildHeartbeat() {
        return buildWCSMessage("HEARTBEAT", timestamp());
    }

    /**
     * Build acknowledgment message.
     * Replaces: fnc_WCS_AcknowledgeMessage
     */
    public static String buildAcknowledgment(String messageId, boolean success) {
        return buildWCSMessage(success ? "ACK" : "NAK", messageId, timestamp());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Checksum
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Calculate checksum (LRC - Longitudinal Redundancy Check).
     * Replaces: fnc_TCP_CalculateChecksum
     */
    public static String calculateChecksum(String data) {
        if (data == null) return "00";
        int lrc = 0;
        for (char c : data.toCharArray()) {
            lrc ^= c;
        }
        return String.format("%02X", lrc & 0xFF);
    }

    /**
     * Validate checksum.
     * Replaces: fnc_TCP_ValidateChecksum
     */
    public static boolean validateChecksum(String message, String expectedChecksum) {
        String calculated = calculateChecksum(message);
        return calculated.equalsIgnoreCase(expectedChecksum);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Message Escaping
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Escape special characters in message.
     * Replaces: fnc_TCP_EscapeMessage
     */
    public static String escapeMessage(String message) {
        if (message == null) return null;
        return message
            .replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("~", "\\~")
            .replace(STX, "\\STX")
            .replace(ETX, "\\ETX");
    }

    /**
     * Unescape special characters.
     * Replaces: fnc_TCP_UnescapeMessage
     */
    public static String unescapeMessage(String message) {
        if (message == null) return null;
        return message
            .replace("\\ETX", ETX)
            .replace("\\STX", STX)
            .replace("\\~", "~")
            .replace("\\|", "|")
            .replace("\\\\", "\\");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Response Parsing
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Parse ASRS response.
     * Replaces: fnc_WCS_ParseASRS_Response
     */
    public static ASRSResponse parseASRSResponse(String response) {
        WCSMessage msg = parseWCSMessage(response);
        if (msg == null) return null;

        ASRSResponse asrsResponse = new ASRSResponse();
        asrsResponse.success = "ACK".equals(msg.messageType);
        asrsResponse.location = msg.fields.get("field1");
        asrsResponse.lpn = msg.fields.get("field2");
        asrsResponse.status = msg.fields.get("field3");
        asrsResponse.errorCode = msg.fields.get("field4");
        return asrsResponse;
    }

    public static class ASRSResponse {
        public boolean success;
        public String location;
        public String lpn;
        public String status;
        public String errorCode;
    }

    /**
     * Check if response is ACK.
     */
    public static boolean isAcknowledged(String response) {
        return response != null && response.contains("ACK");
    }

    /**
     * Check if response is NAK.
     */
    public static boolean isNegativeAck(String response) {
        return response != null && response.contains("NAK");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    /**
     * Generate unique message ID.
     */
    public static String generateMessageId() {
        return "MSG" + timestamp() + String.format("%04d", (int)(Math.random() * 10000));
    }
}
