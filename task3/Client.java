import java.io.*; // ����IO��⣬�����ļ�������IO����
import java.net.Socket; // ����Socket��⣬��������ͨ��
import java.util.ArrayList; // ����ArrayList��⣬���ڴ洢��̬����
import java.util.List; // ����List�ӿ�
import java.util.Random; // ����Random��⣬�������������
import java.nio.ByteBuffer; // ����ByteBuffer��⣬���ڲ����ֽڻ�����

// �ͻ�����
public class Client {
    // ���ļ��ָ�ɶ����ķ���
    private static List<byte[]> splitFileIntoBlocks(File file, int Lmin, int Lmax) throws IOException {
        List<byte[]> blocks = new ArrayList<>(); // ����һ���б����洢�ļ���
        try (FileInputStream fis = new FileInputStream(file)) { // �����ļ�������
            byte[] buffer = new byte[Lmax]; // ����һ����󳤶�ΪLmax�Ļ�����
            int bytesRead; // ���ڴ洢��ȡ���ֽ���
            Random rand = new Random(); // ����һ�������������

            // ѭ����ȡ�ļ���ֱ���ļ�ĩβ
            while ((bytesRead = fis.read(buffer, 0, Lmin + rand.nextInt(Lmax - Lmin + 1))) != -1) {
                byte[] block = new byte[bytesRead]; // ����һ���µ��������洢��ȡ������
                System.arraycopy(buffer, 0, block, 0, bytesRead); // ����ȡ�����ݸ��Ƶ���������
                blocks.add(block); // ����������ӵ��б���
            }
        }
        return blocks; // ���ذ��������ļ�����б�
    }

    // ������
    public static void main(String[] args) {
        // ��������в��������Ƿ���ȷ
        if (args.length != 5) {
            System.out.println("Usage: java ReverseClient <Lmin> <Lmax> <serverIP> <serverPort> <filePath>");
            return; // ��������ȷʱ�˳�����
        }

        // ���������в���
        int Lmin = Integer.parseInt(args[0]); // ��С���С
        int Lmax = Integer.parseInt(args[1]); // �����С
        String serverIP = args[2]; // ������IP��ַ
        int serverPort = Integer.parseInt(args[3]); // �������˿ں�
        String filePath = args[4]; // �ļ�·��

        try {
            File file = new File(filePath); // �����ļ�����
            List<byte[]> blocks = splitFileIntoBlocks(file, Lmin, Lmax); // �ָ��ļ�

            // ����Socket���ӷ�����
            try (Socket socket = new Socket(serverIP, serverPort)) {
                InputStream input = socket.getInputStream(); // ��ȡ������
                OutputStream output = socket.getOutputStream(); // ��ȡ�����
                DataInputStream dataInputStream = new DataInputStream(input); // ��������������
                DataOutputStream dataOutputStream = new DataOutputStream(output); // �������������

                // ���ͳ�ʼ������
                ByteBuffer byteBuffer_init = ByteBuffer.allocate(6); // ����һ��6�ֽڵ�ByteBuffer
                byteBuffer_init.putShort((short)1); // ��short���ͷ���ByteBuffer
                byteBuffer_init.putInt(blocks.size()); // ��int���ͷ���ByteBuffer
                byte[] Initialization = byteBuffer_init.array(); // ��ByteBufferת��Ϊbyte����
                dataOutputStream.write(Initialization); // ���ͳ�ʼ������
                dataOutputStream.flush(); // ˢ�������

                // ��ȡͬ�ⱨ��
                int type = dataInputStream.readShort(); // ��ȡ��������
                if (type != 2) {
                    System.out.println("The initialization fails, and the server refuses to connect");
                    return; // ����������Ͳ���2�����ʼ��ʧ��
                } else {
                    System.out.println("The initialization is successful"); // ��ʼ���ɹ�
                }

                StringBuilder finalReversedContent = new StringBuilder(); // ����StringBuilder�洢���ս��

                // ���������ļ���
                for (int i = 0; i < blocks.size(); i++) {
                    byte[] block = blocks.get(i); // ��ȡ��ǰ��

                    // ����reverseRequest����
                    ByteBuffer byteBuffer_rev = ByteBuffer.allocate(2 + 4 + block.length); // ����ByteBuffer
                    byteBuffer_rev.putShort((short)3); // ��������Ϊ3
                    byteBuffer_rev.putInt(block.length); // ����鳤��
                    byteBuffer_rev.put(block); // ���������
                    byte[] reverseRequest = byteBuffer_rev.array(); // ת��Ϊbyte����
                    dataOutputStream.write(reverseRequest); // ���ͱ���
                    dataOutputStream.flush(); // ˢ�������

                    // ��ȡreverseAnswer����
                    type = dataInputStream.readShort(); // ��ȡ��������
                    int length = dataInputStream.readInt(); // ��ȡ�鳤��
                    byte[] reversedBlock = new byte[length]; // ��������洢��ת��Ŀ�
                    dataInputStream.readFully(reversedBlock); // ��ȡ��ת��Ŀ�
                    String reversedBlockStr = new String(reversedBlock, "UTF-8"); // ת��Ϊ�ַ���

                    // �滻���з��ͻس��������ڴ�ӡ
                    String replacedText = reversedBlockStr.replace("\n", "\\n").replace("\r", "\\r");
                    System.out.println("�� " + (i + 1) + " ��: " + replacedText);
                    finalReversedContent.insert(0, reversedBlockStr); // ����ת������ݲ��뵽��ǰ��
                }

                // �����շ�ת������д���ļ�
                try (FileWriter fw = new FileWriter("reversed_" + file.getName())) {
                    fw.write(finalReversedContent.toString()); // д���ļ�
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // ��ӡ�쳣��Ϣ
        }
    }
}
