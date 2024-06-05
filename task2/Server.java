import java.io.*; // 导入java.io包，包含用于输入输出功能的类
import java.net.*; // 导入java.net包，包含执行网络操作的类
import java.util.Random; // 导入Random类，用于生成随机数
import java.util.concurrent.ExecutorService; // 导入ExecutorService，用于管理线程池
import java.util.concurrent.Executors; // 导入Executors，提供工厂方法来创建线程池

public class Server { // 定义Server类
    static class Packet { // 定义静态内部类Packet，用于表示数据包
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

    // 程序的入口点
    public static void main(String[] args) {
        int port = 12345; // 服务器监听的端口号
        ExecutorService executorService = Executors.newCachedThreadPool(); // 创建一个缓存线程池

        try (DatagramSocket serverSocket = new DatagramSocket(port)) { // 创建一个DatagramSocket来监听指定端口
            System.out.println("Server started and listening on port " + port); // 输出服务器启动的信息

            while (true) { // 服务器无限循环，等待接收数据包
                byte[] buffer = new byte[1024]; // 创建一个字节数组作为接收数据的缓冲区
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length); // 创建一个DatagramPacket来接收数据
                serverSocket.receive(receivePacket); // 接收数据包

                executorService.submit(() -> { // 使用线程池来处理接收到的数据包
                    try {
                        Packet packet = Packet.fromByteArray(receivePacket.getData()); // 从接收到的字节数组中解析出数据包对象
                        System.out.println("Received from client: " + packet + " Retries: " + packet.retries); // 输出接收到的数据包信息

                        Random rand = new Random(); // 创建Random对象，用于生成随机数
                        double probability = rand.nextDouble(); // 生成一个0到1之间的随机数
                        int waitTime; // 声明一个变量用于存储等待时间
                        if (probability < 0.07) { // 如果随机数小于0.07
                            waitTime = 300 + rand.nextInt(31); // 设置等待时间为300到330毫秒之间的随机值
                        } else if (probability < 0.15) { // 如果随机数小于0.15
                            waitTime = 100 + rand.nextInt(101); // 设置等待时间为100到200毫秒之间的随机值
                        } else { // 如果随机数大于或等于0.15
                            waitTime = rand.nextInt(20); // 设置等待时间为0到21毫秒之间的随机值
                        }
                        Thread.sleep(waitTime); // 使当前线程暂停等待时间的毫秒数

                        long serSendTime = System.currentTimeMillis(); // 获取当前系统时间作为服务器发送时间
                        Packet responsePacket = new Packet(packet.sequenceNumber, packet.ver, ("Ack: " + packet.content), packet.sendTime, serSendTime, packet.retries); // 创建一个新的数据包对象作为响应
                        byte[] responseBytes = responsePacket.toByteArray(); // 将响应数据包转换为字节数组
                        DatagramPacket sendPacket = new DatagramPacket(responseBytes, responseBytes.length, receivePacket.getAddress(), receivePacket.getPort()); // 创建一个DatagramPacket来发送响应数据
                        serverSocket.send(sendPacket); // 发送响应数据包
                    } catch (IOException | InterruptedException e) { // 捕获并处理可能发生的异常
                        System.err.println("Error handling client: " + e.getMessage()); // 输出错误信息
                    }
                });
            }
        } catch (IOException e) { // 捕获并处理可能发生的异常
            System.err.println("Server error: " + e.getMessage()); // 输出错误信息
        } finally {
            executorService.shutdown(); // 关闭线程池
        }
    }
}
