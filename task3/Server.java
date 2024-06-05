import java.io.*; // ����IO��⣬�����ļ�������IO����
import java.net.ServerSocket; // ����ServerSocket��⣬���ڼ����˿�
import java.net.Socket; // ����Socket��⣬��������ͨ��
import java.nio.ByteBuffer; // ����ByteBuffer��⣬���ڲ����ֽڻ�����

// ��������
public class Server {
    // ������
    public static void main(String[] args) {
        int port = 12345; // ��������������Ķ˿ں�
        try (ServerSocket serverSocket = new ServerSocket(port)) { // ����ServerSocket����
            System.out.println("Server started and listening on port " + port); // ��ӡ������������Ϣ
            while (true) { // ����ѭ�����ȴ��ͻ�������
                try (Socket clientSocket = serverSocket.accept()) { // ���ܿͻ�������

                    InputStream input = clientSocket.getInputStream(); // ��ȡ������
                    OutputStream output = clientSocket.getOutputStream(); // ��ȡ�����
                    DataInputStream dataInputStream = new DataInputStream(input); // ��������������
                    DataOutputStream dataOutputStream = new DataOutputStream(output); // �������������

                    // ��ȡ��ʼ������
                    byte[] initHeader = new byte[6]; // ����һ��6�ֽڵ��������ڴ洢��ʼ������
                    dataInputStream.readFully(initHeader); // ��ȡ��ʼ������
                    int type = ((initHeader[0] & 0xFF) << 8) | (initHeader[1] & 0xFF); // ������������
                    int N = ((initHeader[2] & 0xFF) << 24) | ((initHeader[3] & 0xFF) << 16) | ((initHeader[4] & 0xFF) << 8) | (initHeader[5] & 0xFF); // ����Nֵ
                    System.out.println("Received Initialization Packet, Type: " + type + ", N: " + N); // ��ӡ���յ��ĳ�ʼ��������Ϣ

                    // ����ͬ�ⱨ��
                    ByteBuffer byteBuffer_arg = ByteBuffer.allocate(2); // ����һ��2�ֽڵ�ByteBuffer
                    byteBuffer_arg.putShort((short)2); // ��short���͵�2����ByteBuffer
                    byte[] agree = byteBuffer_arg.array(); // ��ByteBufferת��Ϊbyte����
                    dataOutputStream.write(agree); // ����ͬ�ⱨ��
                    dataOutputStream.flush(); // ˢ�������

                    for (int i = 0; i < N; i++) { // ѭ��N�Σ�����N�����ݿ�
                        // ��ȡreverseRequest����
                        byte[] header = new byte[6]; // ����һ��6�ֽڵ��������ڴ洢������ͷ
                        dataInputStream.readFully(header); // ��ȡ������ͷ
                        type = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF); // ������������
                        int length = ((header[2] & 0xFF) << 24) | ((header[3] & 0xFF) << 16) | ((header[4] & 0xFF) << 8) | (header[5] & 0xFF); // �������ݿ鳤��
                        byte[] data = new byte[length]; // ����һ���������ڴ洢���ݿ�
                        dataInputStream.readFully(data); // ��ȡ���ݿ�
                        String dataStr = new String(data, "UTF-8"); // �����ݿ�ת��Ϊ�ַ���
                        String replacedText = dataStr.replace("\n", "\\n").replace("\r", "\\r"); // �滻���з��ͻس��������ڴ�ӡ
                        System.out.println("���յ����ݿ� " + (i + 1) + "������: " + replacedText); // ��ӡ���յ������ݿ���Ϣ

                        // ��ת�ַ���
                        String reversedDataStr = new StringBuilder(dataStr).reverse().toString(); // ��ת�ַ���
                        byte[] reversedData = reversedDataStr.getBytes("UTF-8"); // ����ת����ַ���ת��Ϊ�ֽ�����

                        // ����reverseAnswer����
                        ByteBuffer byteBuffer_rev = ByteBuffer.allocate(2 + 4 + reversedData.length); // ����ByteBuffer
                        byteBuffer_rev.putShort((short)4); // ��short���͵�4����ByteBuffer
                        byteBuffer_rev.putInt(reversedData.length); // ����ת������ݿ鳤�ȷ���ByteBuffer
                        byteBuffer_rev.put(reversedData); // ����ת������ݿ����ByteBuffer
                        byte[] reverseAnswer = byteBuffer_rev.array(); // ��ByteBufferת��Ϊbyte����
                        dataOutputStream.write(reverseAnswer); // ���ͷ�ת�𸴱���
                        dataOutputStream.flush(); // ˢ�������
                    }
                    System.out.println("The client disconnects"); // ��ӡ�ͻ��˶Ͽ����ӵ���Ϣ
                } catch (IOException e) {
                    e.printStackTrace(); // ��ӡ�쳣��Ϣ
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // ��ӡ�쳣��Ϣ
        }
    }
}
