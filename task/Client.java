import java.io.*; // ����java.io����������������������ܵ���
import java.net.*; // ����java.net��������ִ�������������
import java.util.concurrent.*; // ����java.util.concurrent�����������ڲ�����̵Ĺ�����
import java.util.ArrayList; // ����ArrayList�࣬���ڴ洢�����б�

public class Client { // ����Client��
    private static final int MAX_RETRIES = 2; // ����������Դ���Ϊ2
    private static final int TIMEOUT = 100; // ���ó�ʱʱ��Ϊ100����

    private static class Packet { // ���徲̬�ڲ���Packet�����ڱ�ʾ���ݰ�
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

    private static class ResendTask implements Runnable { // ����ResendTask�࣬ʵ��Runnable�ӿڣ����ڴ������ݰ����ط�����
        private final Packet packet; // Ҫ�ط������ݰ�
        private final DatagramSocket socket; // UDP�׽���
        private final InetAddress address; // ��������ַ
        private final int port; // �������˿�
        private final ConcurrentLinkedQueue<Packet> resendQueue; // �ط�����
        private final CountDownLatch latch; // ����ʱ������
        private ScheduledFuture<?> future; // ����ȡ�������Future����

        // ResendTask��Ĺ��캯�������ڳ�ʼ���ط�����ĸ�������
        ResendTask(Packet packet, DatagramSocket socket, InetAddress address, int port, ConcurrentLinkedQueue<Packet> resendQueue, CountDownLatch latch) {
            this.packet = packet;
            this.socket = socket;
            this.address = address;
            this.port = port;
            this.resendQueue = resendQueue;
            this.latch = latch;
        }

        // ����future���ԣ�����ȡ������
        public void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        // run������������ִ��ʱ����
        @Override
        public void run() {
            if (resendQueue.contains(packet) && packet.retries < MAX_RETRIES) { // ������ݰ����ط������������Դ���С��������Դ���
                try {
                    packet.retries++; // �������Դ���
                    sequenceNumber++; // �������к�
                    packet.sequenceNumber = sequenceNumber; // �������ݰ������к�
                    System.out.println("ReSending packet: " + packet + " Retries: " + packet.retries); // ��ӡ�ط���Ϣ
                    byte[] data = packet.toByteArray(); // �����ݰ�ת��Ϊ�ֽ�����
                    DatagramPacket sendPacket = new DatagramPacket(data, data.length, address, port); // ����DatagramPacket����
                    socket.send(sendPacket); // �������ݰ�
                    sendNum++; // ���ӷ��ͼ���
                } catch (IOException e) {
                    e.printStackTrace(); // ��ӡ�쳣��Ϣ
                }
            } else if (packet.retries >= MAX_RETRIES) { // ������Դ����ﵽ���ֵ
                System.out.println("Maximum retries reached for packet: " + packet); // ��ӡ�ﵽ������Դ�������Ϣ
                resendQueue.remove(packet); // ���ط��������Ƴ����ݰ�
                if (future != null) {
                    future.cancel(false); // ȡ������
                }
                latch.countDown(); // ���ٵ���ʱ�������ļ���
            }
        }
    }

    // ���徲̬���������ڸ������ݰ������к�
    static short sequenceNumber = 0;
    // ���徲̬���������ڼ�¼���յ������ݰ�����
    static int revNum = 0;
    // ���徲̬���������ڼ�¼���͵����ݰ�����
    static int sendNum = 0;
    // ���徲̬��־λ�����ڱ���Ƿ��յ��˵�һ������������ʱ��
    static boolean serFirstSendTime_flag = false;
    // ���徲̬���������ڼ�¼��һ������������ʱ��
    static long serFirstSendTime = 0;
    // ���徲̬���������ڼ�¼���һ������������ʱ��
    static long serLastSendTime = 0;
    // ���徲̬�б����ڴ洢����ʱ��(RTT)���б�
    static ArrayList<Long> rttList = new ArrayList<>();
    // ���徲̬���������ڼ�¼�������ʱ��
    static long maxRtt = 0;
    // ���徲̬���������ڼ�¼��С����ʱ��
    static long minRtt = 1000;

    // �������ڵ�
    public static void main(String[] args) {
        // ��������в����Ƿ���ȷ
        if (args.length != 2) {
            System.err.println("Usage: java Client <IP address> <Port number>");
            System.exit(1);
        }

        // �������в�����ȡ��������ַ�Ͷ˿ں�
        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);

        try {
            // ��ȡ��������InetAddress����
            InetAddress address = InetAddress.getByName(serverAddress);
            // ����UDP�׽���
            DatagramSocket socket = new DatagramSocket();

            // ����ScheduledExecutorService�������ڵ�������
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(12);
            // ����ConcurrentLinkedQueue�������ڴ洢��Ҫ�ط������ݰ�
            ConcurrentLinkedQueue<Packet> resendQueue = new ConcurrentLinkedQueue<>();
            // ����CountDownLatch�������ڵȴ������������
            CountDownLatch latch = new CountDownLatch(12);

            // ���������߳�
            Thread receiverThread = new Thread(() -> {
                try {
                    // �����ֽ�������Ϊ���ջ�����
                    byte[] buffer = new byte[1024];
                    // ����DatagramPacket����
                    DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);

                    // ����ѭ�����ȴ��������ݰ�
                    while (true) {
                        // �������ݰ�
                        socket.receive(receivePacket);
                        // �ӽ��յ����ֽ������н��������ݰ�����
                        Packet responsePacket = Packet.fromByteArray(receivePacket.getData());
                        // ��ȡ��ǰϵͳʱ��
                        long receiveTime = System.currentTimeMillis();
                        // ��������ʱ��
                        long rtt = receiveTime - responsePacket.sendTime - ((long) responsePacket.retries * TIMEOUT);
                        // ����RTT��ֵ��ӡ��ͬ����Ϣ
                        if (rtt < 100)
                            System.out.println("Received ack for packet: " + responsePacket.sequenceNumber + " Ver=" + responsePacket.ver + " " + responsePacket.content + " RTT: " + rtt + "ms" + " Retries:" + responsePacket.retries);
                        else
                            System.out.println("Received ack for packet: " + responsePacket.sequenceNumber + " Ver=" + responsePacket.ver + " " + responsePacket.content + " RTT: " + rtt + "ms" + " Retries:" + responsePacket.retries + " Request time out");

                        // ���RTTС��100���룬����ͳ������
                        if (rtt < 100) {
                            revNum++;
                            rttList.add(rtt);
                            if (rtt > maxRtt)
                                maxRtt = rtt;
                            if (rtt < minRtt)
                                minRtt = rtt;
                        }
                        // ����ǵ�һ�ν��յ�����������ʱ��
                        if (!serFirstSendTime_flag) {
                            serFirstSendTime_flag = true;
                            serFirstSendTime = responsePacket.serSendTime;
                        }
                        // �������һ������������ʱ��
                        serLastSendTime = responsePacket.serSendTime;

                        // ���ط������в���ԭʼ���ݰ�
                        Packet originalPacket = resendQueue.stream()
                                .filter(p -> p.sequenceNumber == responsePacket.sequenceNumber)
                                .findFirst()
                                .orElse(null);

                        // ����ҵ�ԭʼ���ݰ�������RTTС��100��������Դ����ﵽ2��
                        if (originalPacket != null && (rtt < 100 || responsePacket.retries >= 2)) {
                            resendQueue.remove(originalPacket);
                            latch.countDown();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            // ���������߳�
            receiverThread.start();

            // ѭ������12�����ݰ�
            for (int i = 0; i < 12; i++) {
                // �����µ����ݰ�����
                Packet packet = new Packet(++sequenceNumber, (byte) 2, "Hello " + i, System.currentTimeMillis(), 0, 0);
                // ��ӡ������Ϣ
                System.out.println("Sending packet: " + packet);
                // �����ݰ�ת��Ϊ�ֽ�����
                byte[] data = packet.toByteArray();
                // ����DatagramPacket����
                DatagramPacket sendPacket = new DatagramPacket(data, data.length, address, port);
                // �������ݰ�
                socket.send(sendPacket);
                // ���ӷ��ͼ���
                sendNum++;

                // �����ط��������
                ResendTask resendTask = new ResendTask(packet, socket, address, port, resendQueue, latch);
                // �����ط�����
                ScheduledFuture<?> future = executor.scheduleAtFixedRate(resendTask, TIMEOUT, TIMEOUT, TimeUnit.MILLISECONDS);
                // �����ط������future����
                resendTask.setFuture(future);
                // �����ݰ���ӵ��ط�������
                resendQueue.add(packet);
            }

            // �ȴ������������
            latch.await();
            // �رյ�����
            executor.shutdownNow();
            // �ر�UDP�׽���
            socket.close();

            // ��ӡ���պͷ��͵����ݰ�����
            System.out.println("Number of UDP packets received: " + revNum);
            System.out.println("The number of UDP packets sent: " + sendNum);
            // ���㲢��ӡ���ݰ���ʧ��
            double lossRate = 1 - ((double) revNum / sendNum);
            System.out.println("Packet loss rate " + String.format("%.2f", lossRate));
            // ��ӡ����Ӧʱ��
            long overallResTime = serLastSendTime - serFirstSendTime;
            System.out.println("Overall response time: " + overallResTime + " ms");
            // ����ƽ������ʱ��
            long sum = 0;
            for (Long value : rttList) {
                sum += value;
            }
            double mean = (double) sum / rttList.size();
            // ��������ʱ��ķ���
            double variance = 0;
            for (Long value : rttList) {
                variance += Math.pow(value - mean, 2);
            }
            variance /= rttList.size();
            // ��������ʱ��ı�׼��
            double standardDeviation = Math.sqrt(variance);

            // ��ӡ�����С��ƽ������ʱ�䣬����ͱ�׼��
            System.out.println("Maximum RTT: " + maxRtt + " ms");
            System.out.println("Minimum RTT: " + minRtt + " ms");
            System.out.println("RTT average: " + String.format("%.2f", mean) + " ms");
            System.out.println("RTT variance: " + String.format("%.2f", variance) + " ms^2");
            System.out.println("RTT Standard deviation: " + String.format("%.2f", standardDeviation) + " ms");

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}