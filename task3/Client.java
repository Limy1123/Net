import java.io.*; // 导入IO类库，用于文件和网络IO操作
import java.net.Socket; // 导入Socket类库，用于网络通信
import java.util.ArrayList; // 导入ArrayList类库，用于存储动态数组
import java.util.List; // 导入List接口
import java.util.Random; // 导入Random类库，用于生成随机数
import java.nio.ByteBuffer; // 导入ByteBuffer类库，用于操作字节缓冲区

// 客户端类
public class Client {
    // 将文件分割成多个块的方法
    private static List<byte[]> splitFileIntoBlocks(File file, int Lmin, int Lmax) throws IOException {
        List<byte[]> blocks = new ArrayList<>(); // 创建一个列表来存储文件块
        try (FileInputStream fis = new FileInputStream(file)) { // 创建文件输入流
            byte[] buffer = new byte[Lmax]; // 创建一个最大长度为Lmax的缓冲区
            int bytesRead; // 用于存储读取的字节数
            Random rand = new Random(); // 创建一个随机数生成器

            // 循环读取文件，直到文件末尾
            while ((bytesRead = fis.read(buffer, 0, Lmin + rand.nextInt(Lmax - Lmin + 1))) != -1) {
                byte[] block = new byte[bytesRead]; // 创建一个新的数组来存储读取的数据
                System.arraycopy(buffer, 0, block, 0, bytesRead); // 将读取的数据复制到新数组中
                blocks.add(block); // 将新数组添加到列表中
            }
        }
        return blocks; // 返回包含所有文件块的列表
    }

    // 主方法
    public static void main(String[] args) {
        // 检查命令行参数数量是否正确
        if (args.length != 5) {
            System.out.println("Usage: java ReverseClient <Lmin> <Lmax> <serverIP> <serverPort> <filePath>");
            return; // 参数不正确时退出程序
        }

        // 解析命令行参数
        int Lmin = Integer.parseInt(args[0]); // 最小块大小
        int Lmax = Integer.parseInt(args[1]); // 最大块大小
        String serverIP = args[2]; // 服务器IP地址
        int serverPort = Integer.parseInt(args[3]); // 服务器端口号
        String filePath = args[4]; // 文件路径

        try {
            File file = new File(filePath); // 创建文件对象
            List<byte[]> blocks = splitFileIntoBlocks(file, Lmin, Lmax); // 分割文件

            // 创建Socket连接服务器
            try (Socket socket = new Socket(serverIP, serverPort)) {
                InputStream input = socket.getInputStream(); // 获取输入流
                OutputStream output = socket.getOutputStream(); // 获取输出流
                DataInputStream dataInputStream = new DataInputStream(input); // 创建数据输入流
                DataOutputStream dataOutputStream = new DataOutputStream(output); // 创建数据输出流

                // 发送初始化报文
                ByteBuffer byteBuffer_init = ByteBuffer.allocate(6); // 创建一个6字节的ByteBuffer
                byteBuffer_init.putShort((short)1); // 将short类型放入ByteBuffer
                byteBuffer_init.putInt(blocks.size()); // 将int类型放入ByteBuffer
                byte[] Initialization = byteBuffer_init.array(); // 将ByteBuffer转换为byte数组
                dataOutputStream.write(Initialization); // 发送初始化报文
                dataOutputStream.flush(); // 刷新输出流

                // 读取同意报文
                int type = dataInputStream.readShort(); // 读取报文类型
                if (type != 2) {
                    System.out.println("The initialization fails, and the server refuses to connect");
                    return; // 如果报文类型不是2，则初始化失败
                } else {
                    System.out.println("The initialization is successful"); // 初始化成功
                }

                StringBuilder finalReversedContent = new StringBuilder(); // 创建StringBuilder存储最终结果

                // 遍历所有文件块
                for (int i = 0; i < blocks.size(); i++) {
                    byte[] block = blocks.get(i); // 获取当前块

                    // 发送reverseRequest报文
                    ByteBuffer byteBuffer_rev = ByteBuffer.allocate(2 + 4 + block.length); // 创建ByteBuffer
                    byteBuffer_rev.putShort((short)3); // 报文类型为3
                    byteBuffer_rev.putInt(block.length); // 放入块长度
                    byteBuffer_rev.put(block); // 放入块内容
                    byte[] reverseRequest = byteBuffer_rev.array(); // 转换为byte数组
                    dataOutputStream.write(reverseRequest); // 发送报文
                    dataOutputStream.flush(); // 刷新输出流

                    // 读取reverseAnswer报文
                    type = dataInputStream.readShort(); // 读取报文类型
                    int length = dataInputStream.readInt(); // 读取块长度
                    byte[] reversedBlock = new byte[length]; // 创建数组存储反转后的块
                    dataInputStream.readFully(reversedBlock); // 读取反转后的块
                    String reversedBlockStr = new String(reversedBlock, "UTF-8"); // 转换为字符串

                    // 替换换行符和回车符，用于打印
                    String replacedText = reversedBlockStr.replace("\n", "\\n").replace("\r", "\\r");
                    System.out.println("第 " + (i + 1) + " 块: " + replacedText);
                    finalReversedContent.insert(0, reversedBlockStr); // 将反转后的内容插入到最前面
                }

                // 将最终反转的内容写入文件
                try (FileWriter fw = new FileWriter("reversed_" + file.getName())) {
                    fw.write(finalReversedContent.toString()); // 写入文件
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // 打印异常信息
        }
    }
}
