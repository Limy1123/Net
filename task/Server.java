import java.io.*; // ����java.io����������������������ܵ���
import java.net.*; // ����java.net��������ִ�������������
import java.util.Random; // ����Random�࣬�������������
import java.util.concurrent.ExecutorService; // ����ExecutorService�����ڹ����̳߳�
import java.util.concurrent.Executors; // ����Executors���ṩ���������������̳߳�

public class Server { // ����Server��
    static class Packet { // ���徲̬�ڲ���Packet�����ڱ�ʾ���ݰ�
        short sequenceNumber; // ���ݰ������к�
        byte ver; // ���ݰ��İ汾��
        String content; // ���ݰ�������
        long sendTime; // �������ݰ���ʱ���
        long serSendTime; // �������������ݰ���ʱ���
        int retries; // ���ݰ������Դ���

        // Packet��Ĺ��캯�������ڳ�ʼ�����ݰ��ĸ�������
        Packet(short sequenceNumber, byte ver, String content, long sendTime, long serSendTime, int retries) {
            this.sequenceNumber = sequenceNumber;
            this.ver = ver;
            this.content = content;
            this.sendTime = sendTime;
            this.serSendTime = serSendTime;
            this.retries = retries;
        }

        // ��дtoString���������ڷ������ݰ����ַ�����ʾ��ʽ
        @Override
        public String toString() {
            return sequenceNumber + ", ver=" + ver + ", " + content;
        }

        // �����ݰ�����ת��Ϊ�ֽ����飬�Ա�ͨ�����緢��
        public byte[] toByteArray() throws IOException {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteStream);
            out.writeShort(sequenceNumber);
            out.writeByte(ver);
            out.writeUTF(content);
            out.writeLong(sendTime);
            out.writeLong(serSendTime);
            out.writeInt(retries);
            out.close();
            return byteStream.toByteArray();
        }

        // ���ֽ������н��������ݰ�����
        public static Packet fromByteArray(byte[] byteArray) throws IOException {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(byteArray);
            DataInputStream in = new DataInputStream(byteStream);
            short sequenceNumber = in.readShort();
            byte ver = in.readByte();
            String content = in.readUTF();
            long sendTime = in.readLong();
            long serSendTime = in.readLong();
            int retries = in.readInt();
            in.close();
            return new Packet(sequenceNumber, ver, content, sendTime, serSendTime, retries);
        }
    }

    // �������ڵ�
    public static void main(String[] args) {
        int port = 12345; // �����������Ķ˿ں�
        ExecutorService executorService = Executors.newCachedThreadPool(); // ����һ�������̳߳�

        try (DatagramSocket serverSocket = new DatagramSocket(port)) { // ����һ��DatagramSocket������ָ���˿�
            System.out.println("Server started and listening on port " + port); // �����������������Ϣ

            while (true) { // ����������ѭ�����ȴ��������ݰ�
                byte[] buffer = new byte[1024]; // ����һ���ֽ�������Ϊ�������ݵĻ�����
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length); // ����һ��DatagramPacket����������
                serverSocket.receive(receivePacket); // �������ݰ�

                executorService.submit(() -> { // ʹ���̳߳���������յ������ݰ�
                    try {
                        Packet packet = Packet.fromByteArray(receivePacket.getData()); // �ӽ��յ����ֽ������н��������ݰ�����
                        System.out.println("Received from client: " + packet + " Retries: " + packet.retries); // ������յ������ݰ���Ϣ

                        Random rand = new Random(); // ����Random�����������������
                        double probability = rand.nextDouble(); // ����һ��0��1֮��������
                        int waitTime; // ����һ���������ڴ洢�ȴ�ʱ��
                        if (probability < 0.07) { // ��������С��0.07
                            waitTime = 300 + rand.nextInt(31); // ���õȴ�ʱ��Ϊ300��330����֮������ֵ
                        } else if (probability < 0.15) { // ��������С��0.15
                            waitTime = 100 + rand.nextInt(101); // ���õȴ�ʱ��Ϊ100��200����֮������ֵ
                        } else { // �����������ڻ����0.15
                            waitTime = rand.nextInt(20); // ���õȴ�ʱ��Ϊ0��21����֮������ֵ
                        }
                        Thread.sleep(waitTime); // ʹ��ǰ�߳���ͣ�ȴ�ʱ��ĺ�����

                        long serSendTime = System.currentTimeMillis(); // ��ȡ��ǰϵͳʱ����Ϊ����������ʱ��
                        Packet responsePacket = new Packet(packet.sequenceNumber, packet.ver, ("Ack: " + packet.content), packet.sendTime, serSendTime, packet.retries); // ����һ���µ����ݰ�������Ϊ��Ӧ
                        byte[] responseBytes = responsePacket.toByteArray(); // ����Ӧ���ݰ�ת��Ϊ�ֽ�����
                        DatagramPacket sendPacket = new DatagramPacket(responseBytes, responseBytes.length, receivePacket.getAddress(), receivePacket.getPort()); // ����һ��DatagramPacket��������Ӧ����
                        serverSocket.send(sendPacket); // ������Ӧ���ݰ�
                    } catch (IOException | InterruptedException e) { // ���񲢴�����ܷ������쳣
                        System.err.println("Error handling client: " + e.getMessage()); // ���������Ϣ
                    }
                });
            }
        } catch (IOException e) { // ���񲢴�����ܷ������쳣
            System.err.println("Server error: " + e.getMessage()); // ���������Ϣ
        } finally {
            executorService.shutdown(); // �ر��̳߳�
        }
    }
}
