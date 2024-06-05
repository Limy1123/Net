import java.io.*; // 导入IO类库，用于文件和网络IO操作
import java.net.ServerSocket; // 导入ServerSocket类库，用于监听端口
import java.net.Socket; // 导入Socket类库，用于网络通信
import java.nio.ByteBuffer; // 导入ByteBuffer类库，用于操作字节缓冲区

// 服务器类
public class Server {
    // 主方法
    public static void main(String[] args) {
        int port = 12345; // 定义服务器监听的端口号
        try (ServerSocket serverSocket = new ServerSocket(port)) { // 创建ServerSocket对象
            System.out.println("Server started and listening on port " + port); // 打印服务器启动信息
            while (true) { // 无限循环，等待客户端连接
                try (Socket clientSocket = serverSocket.accept()) { // 接受客户端连接

                    InputStream input = clientSocket.getInputStream(); // 获取输入流
                    OutputStream output = clientSocket.getOutputStream(); // 获取输出流
                    DataInputStream dataInputStream = new DataInputStream(input); // 创建数据输入流
                    DataOutputStream dataOutputStream = new DataOutputStream(output); // 创建数据输出流

                    // 读取初始化报文
                    byte[] initHeader = new byte[6]; // 创建一个6字节的数组用于存储初始化报文
                    dataInputStream.readFully(initHeader); // 读取初始化报文
                    int type = ((initHeader[0] & 0xFF) << 8) | (initHeader[1] & 0xFF); // 解析报文类型
                    int N = ((initHeader[2] & 0xFF) << 24) | ((initHeader[3] & 0xFF) << 16) | ((initHeader[4] & 0xFF) << 8) | (initHeader[5] & 0xFF); // 解析N值
                    System.out.println("Received Initialization Packet, Type: " + type + ", N: " + N); // 打印接收到的初始化报文信息

                    // 发送同意报文
                    ByteBuffer byteBuffer_arg = ByteBuffer.allocate(2); // 创建一个2字节的ByteBuffer
                    byteBuffer_arg.putShort((short)2); // 将short类型的2放入ByteBuffer
                    byte[] agree = byteBuffer_arg.array(); // 将ByteBuffer转换为byte数组
                    dataOutputStream.write(agree); // 发送同意报文
                    dataOutputStream.flush(); // 刷新输出流

                    for (int i = 0; i < N; i++) { // 循环N次，处理N个数据块
                        // 读取reverseRequest报文
                        byte[] header = new byte[6]; // 创建一个6字节的数组用于存储请求报文头
                        dataInputStream.readFully(header); // 读取请求报文头
                        type = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF); // 解析报文类型
                        int length = ((header[2] & 0xFF) << 24) | ((header[3] & 0xFF) << 16) | ((header[4] & 0xFF) << 8) | (header[5] & 0xFF); // 解析数据块长度
                        byte[] data = new byte[length]; // 创建一个数组用于存储数据块
                        dataInputStream.readFully(data); // 读取数据块
                        String dataStr = new String(data, "UTF-8"); // 将数据块转换为字符串
                        String replacedText = dataStr.replace("\n", "\\n").replace("\r", "\\r"); // 替换换行符和回车符，用于打印
                        System.out.println("接收到数据块 " + (i + 1) + "，内容: " + replacedText); // 打印接收到的数据块信息

                        // 反转字符串
                        String reversedDataStr = new StringBuilder(dataStr).reverse().toString(); // 反转字符串
                        byte[] reversedData = reversedDataStr.getBytes("UTF-8"); // 将反转后的字符串转换为字节数组

                        // 发送reverseAnswer报文
                        ByteBuffer byteBuffer_rev = ByteBuffer.allocate(2 + 4 + reversedData.length); // 创建ByteBuffer
                        byteBuffer_rev.putShort((short)4); // 将short类型的4放入ByteBuffer
                        byteBuffer_rev.putInt(reversedData.length); // 将反转后的数据块长度放入ByteBuffer
                        byteBuffer_rev.put(reversedData); // 将反转后的数据块放入ByteBuffer
                        byte[] reverseAnswer = byteBuffer_rev.array(); // 将ByteBuffer转换为byte数组
                        dataOutputStream.write(reverseAnswer); // 发送反转答复报文
                        dataOutputStream.flush(); // 刷新输出流
                    }
                    System.out.println("The client disconnects"); // 打印客户端断开连接的信息
                } catch (IOException e) {
                    e.printStackTrace(); // 打印异常信息
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // 打印异常信息
        }
    }
}
