import java.io.*; // 导入java.io包，包含用于输入输出功能的类
import java.net.*; // 导入java.net包，包含执行网络操作的类
import java.util.concurrent.*; // 导入java.util.concurrent包，包含用于并发编程的工具类
import java.util.ArrayList; // 导入ArrayList类，用于存储对象列表

public class Client { // 定义Client类
    private static final int MAX_RETRIES = 2; // 设置最大重试次数为2
    private static final int TIMEOUT = 100; // 设置超时时间为100毫秒

    private static class Packet { // 定义静态内部类Packet，用于表示数据包
        short sequenceNumber; // 数据包的序列号
        byte ver; // 数据包的版本号
        String content; // 数据包的内容
        long sendTime; // 发送数据包的时间戳
        long serSendTime; // 服务器发送数据包的时间戳
        int retries; // 数据包的重试次数

        // Packet类的构造函数，用于初始化数据包的各个属性
        Packet(short sequenceNumber, byte ver, String content, long sendTime, long serSendTime, int retries) {
            this.sequenceNumber = sequenceNumber;
            this.ver = ver;
            this.content = content;
            this.sendTime = sendTime;
            this.serSendTime = serSendTime;
            this.retries = retries;
        }

        // 重写toString方法，用于返回数据包的字符串表示形式
        @Override
        public String toString() {
            return sequenceNumber + ", ver=" + ver + ", " + content;
        }

        // 将数据包对象转换为字节数组，以便通过网络发送
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

        // 从字节数组中解析出数据包对象
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

    private static class ResendTask implements Runnable { // 定义ResendTask类，实现Runnable接口，用于处理数据包的重发任务
        private final Packet packet; // 要重发的数据包
        private final DatagramSocket socket; // UDP套接字
        private final InetAddress address; // 服务器地址
        private final int port; // 服务器端口
        private final ConcurrentLinkedQueue<Packet> resendQueue; // 重发队列
        private final CountDownLatch latch; // 倒计时锁存器
        private ScheduledFuture<?> future; // 用于取消任务的Future对象

        // ResendTask类的构造函数，用于初始化重发任务的各个属性
        ResendTask(Packet packet, DatagramSocket socket, InetAddress address, int port, ConcurrentLinkedQueue<Packet> resendQueue, CountDownLatch latch) {
            this.packet = packet;
            this.socket = socket;
            this.address = address;
            this.port = port;
            this.resendQueue = resendQueue;
            this.latch = latch;
        }

        // 设置future属性，用于取消任务
        public void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        // run方法，当任务执行时调用
        @Override
        public void run() {
            if (resendQueue.contains(packet) && packet.retries < MAX_RETRIES) { // 如果数据包在重发队列中且重试次数小于最大重试次数
                try {
                    packet.retries++; // 增加重试次数
                    sequenceNumber++; // 增加序列号
                    packet.sequenceNumber = sequenceNumber; // 更新数据包的序列号
                    System.out.println("ReSending packet: " + packet + " Retries: " + packet.retries); // 打印重发信息
                    byte[] data = packet.toByteArray(); // 将数据包转换为字节数组
                    DatagramPacket sendPacket = new DatagramPacket(data, data.length, address, port); // 创建DatagramPacket对象
                    socket.send(sendPacket); // 发送数据包
                    sendNum++; // 增加发送计数
                } catch (IOException e) {
                    e.printStackTrace(); // 打印异常信息
                }
            } else if (packet.retries >= MAX_RETRIES) { // 如果重试次数达到最大值
                System.out.println("Maximum retries reached for packet: " + packet); // 打印达到最大重试次数的信息
                resendQueue.remove(packet); // 从重发队列中移除数据包
                if (future != null) {
                    future.cancel(false); // 取消任务
                }
                latch.countDown(); // 减少倒计时锁存器的计数
            }
        }
    }

    // 定义静态变量，用于跟踪数据包的序列号
    static short sequenceNumber = 0;
    // 定义静态变量，用于记录接收到的数据包数量
    static int revNum = 0;
    // 定义静态变量，用于记录发送的数据包数量
    static int sendNum = 0;
    // 定义静态标志位，用于标记是否收到了第一个服务器发送时间
    static boolean serFirstSendTime_flag = false;
    // 定义静态变量，用于记录第一个服务器发送时间
    static long serFirstSendTime = 0;
    // 定义静态变量，用于记录最后一个服务器发送时间
    static long serLastSendTime = 0;
    // 定义静态列表，用于存储往返时间(RTT)的列表
    static ArrayList<Long> rttList = new ArrayList<>();
    // 定义静态变量，用于记录最大往返时间
    static long maxRtt = 0;
    // 定义静态变量，用于记录最小往返时间
    static long minRtt = 1000;

    // 程序的入口点
    public static void main(String[] args) {
        // 检查命令行参数是否正确
        if (args.length != 2) {
            System.err.println("Usage: java Client <IP address> <Port number>");
            System.exit(1);
        }

        // 从命令行参数获取服务器地址和端口号
        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);

        try {
            // 获取服务器的InetAddress对象
            InetAddress address = InetAddress.getByName(serverAddress);
            // 创建UDP套接字
            DatagramSocket socket = new DatagramSocket();

            // 创建ScheduledExecutorService对象，用于调度任务
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(12);
            // 创建ConcurrentLinkedQueue对象，用于存储需要重发的数据包
            ConcurrentLinkedQueue<Packet> resendQueue = new ConcurrentLinkedQueue<>();
            // 创建CountDownLatch对象，用于等待所有任务完成
            CountDownLatch latch = new CountDownLatch(12);

            // 创建接收线程
            Thread receiverThread = new Thread(() -> {
                try {
                    // 创建字节数组作为接收缓冲区
                    byte[] buffer = new byte[1024];
                    // 创建DatagramPacket对象
                    DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);

                    // 无限循环，等待接收数据包
                    while (true) {
                        // 接收数据包
                        socket.receive(receivePacket);
                        // 从接收到的字节数组中解析出数据包对象
                        Packet responsePacket = Packet.fromByteArray(receivePacket.getData());
                        // 获取当前系统时间
                        long receiveTime = System.currentTimeMillis();
                        // 计算往返时间
                        long rtt = receiveTime - responsePacket.sendTime - ((long) responsePacket.retries * TIMEOUT);
                        // 根据RTT的值打印不同的信息
                        if (rtt < 100)
                            System.out.println("Received ack for packet: " + responsePacket.sequenceNumber + " Ver=" + responsePacket.ver + " " + responsePacket.content + " RTT: " + rtt + "ms" + " Retries:" + responsePacket.retries);
                        else
                            System.out.println("Received ack for packet: " + responsePacket.sequenceNumber + " Ver=" + responsePacket.ver + " " + responsePacket.content + " RTT: " + rtt + "ms" + " Retries:" + responsePacket.retries + " Request time out");

                        // 如果RTT小于100毫秒，更新统计数据
                        if (rtt < 100) {
                            revNum++;
                            rttList.add(rtt);
                            if (rtt > maxRtt)
                                maxRtt = rtt;
                            if (rtt < minRtt)
                                minRtt = rtt;
                        }
                        // 如果是第一次接收到服务器发送时间
                        if (!serFirstSendTime_flag) {
                            serFirstSendTime_flag = true;
                            serFirstSendTime = responsePacket.serSendTime;
                        }
                        // 更新最后一个服务器发送时间
                        serLastSendTime = responsePacket.serSendTime;

                        // 从重发队列中查找原始数据包
                        Packet originalPacket = resendQueue.stream()
                                .filter(p -> p.sequenceNumber == responsePacket.sequenceNumber)
                                .findFirst()
                                .orElse(null);

                        // 如果找到原始数据包，并且RTT小于100毫秒或重试次数达到2次
                        if (originalPacket != null && (rtt < 100 || responsePacket.retries >= 2)) {
                            resendQueue.remove(originalPacket);
                            latch.countDown();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            // 启动接收线程
            receiverThread.start();

            // 循环发送12个数据包
            for (int i = 0; i < 12; i++) {
                // 创建新的数据包对象
                Packet packet = new Packet(++sequenceNumber, (byte) 2, "Hello " + i, System.currentTimeMillis(), 0, 0);
                // 打印发送信息
                System.out.println("Sending packet: " + packet);
                // 将数据包转换为字节数组
                byte[] data = packet.toByteArray();
                // 创建DatagramPacket对象
                DatagramPacket sendPacket = new DatagramPacket(data, data.length, address, port);
                // 发送数据包
                socket.send(sendPacket);
                // 增加发送计数
                sendNum++;

                // 创建重发任务对象
                ResendTask resendTask = new ResendTask(packet, socket, address, port, resendQueue, latch);
                // 调度重发任务
                ScheduledFuture<?> future = executor.scheduleAtFixedRate(resendTask, TIMEOUT, TIMEOUT, TimeUnit.MILLISECONDS);
                // 设置重发任务的future属性
                resendTask.setFuture(future);
                // 将数据包添加到重发队列中
                resendQueue.add(packet);
            }

            // 等待所有任务完成
            latch.await();
            // 关闭调度器
            executor.shutdownNow();
            // 关闭UDP套接字
            socket.close();

            // 打印接收和发送的数据包数量
            System.out.println("Number of UDP packets received: " + revNum);
            System.out.println("The number of UDP packets sent: " + sendNum);
            // 计算并打印数据包丢失率
            double lossRate = 1 - ((double) revNum / sendNum);
            System.out.println("Packet loss rate " + String.format("%.2f", lossRate));
            // 打印总响应时间
            long overallResTime = serLastSendTime - serFirstSendTime;
            System.out.println("Overall response time: " + overallResTime + " ms");
            // 计算平均往返时间
            long sum = 0;
            for (Long value : rttList) {
                sum += value;
            }
            double mean = (double) sum / rttList.size();
            // 计算往返时间的方差
            double variance = 0;
            for (Long value : rttList) {
                variance += Math.pow(value - mean, 2);
            }
            variance /= rttList.size();
            // 计算往返时间的标准差
            double standardDeviation = Math.sqrt(variance);

            // 打印最大、最小、平均往返时间，方差和标准差
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