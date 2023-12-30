import java.io.*;
import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;

public class vPartition implements Serializable {
	public static final int blockSize = 4096;  // 4096 bits (512 Bytes)
	private final char partitionLabel;
	private final UUID uuid;
	private final long partitionSize;
	private long usedSpace;
	private long freeSpace;
	private int numOfFats = 255;
	transient private final RandomAccessFile partitionHead;
	transient private final FileAllocationTable fat;
	public vPartition(String uuid_string) throws FileNotFoundException {
		String filePath = "%s.vpar".formatted(uuid_string);
		File file = new File(filePath);
		if (!file.isFile())
			throw new FileNotFoundException(filePath);
		try {
			this.partitionHead = new RandomAccessFile(file, "rw");
			try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(readBlock(0)))) {
				vPartition deserialized = (vPartition) ois.readObject();
				this.uuid = deserialized.uuid;
				this.partitionLabel = deserialized.partitionLabel;
				this.partitionSize = deserialized.partitionSize;
			}

			this.fat = deserializeFat();

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public vPartition(char driveLabel, long partitionSize) throws FileAlreadyExistsException {
		this.uuid = UUID.randomUUID();
		String filePath = "%s.vpar".formatted(this.uuid);
		File file = new File(filePath);

		if (file.isFile())
			throw new FileAlreadyExistsException(filePath);

		try {
			this.partitionHead = new RandomAccessFile(file, "rw");
			this.partitionHead.setLength(partitionSize);
			this.partitionLabel = driveLabel;
			this.partitionSize = partitionSize;
			this.fat = new FileAllocationTable((int) Math.ceilDiv(partitionSize, blockSize));
			serializeFat();
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
				oos.writeObject(this);
				writeBlock(0, baos.toByteArray());
			}
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
				oos.writeObject(this.fat);
				writeBlock(1, baos.toByteArray());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void serializeFat() {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(this.fat);

			byte[] serializedData = baos.toByteArray();

			for (int i = 0; i < Math.ceilDiv(serializedData.length, blockSize); i++) {
				byte[] chunk = new byte[blockSize];
				int startIdx = (i * blockSize);
				int endIdx = Math.min((i + 1) * blockSize, serializedData.length);
				System.arraycopy(serializedData, startIdx, chunk, 0, endIdx - startIdx);
				writeBlock(i + 1, chunk);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public FileAllocationTable deserializeFat() {
		byte[] obj = new byte[blockSize * numOfFats];

		try {
			for (int i = 0; i < numOfFats; i++) {
				byte[] chunk = readBlock(i + 1);
				System.arraycopy(chunk, 0, obj, i * blockSize, chunk.length);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try (ByteArrayInputStream bais = new ByteArrayInputStream(obj);
		     ObjectInputStream ois = new ObjectInputStream(bais)) {
			return (FileAllocationTable) ois.readObject();
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public void createFile(String fileName) {
		if (fat.getFileStartBlock(fileName) != -1)
			return;
		fat.createFile(fileName, -1);
	}

	public void saveFileData(String fileName, byte[] data) {
		if (data.length == 0)
			fat.createFile(fileName, -1);

		int[] allocatedBlocks = new int[Math.ceilDiv(data.length, blockSize)];

		Arrays.setAll(allocatedBlocks, i -> fat.allocateBlock());

		int dataIndex = 0;
		for (int i = 0; i < allocatedBlocks.length; i++) {
			int BlockIndex = allocatedBlocks[i];
			byte[] BlockData = Arrays.copyOfRange(data, dataIndex, dataIndex + blockSize);

			try {
				writeBlock(BlockIndex, BlockData);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			dataIndex += blockSize;

			if (i < allocatedBlocks.length - 1) {
				int nextBlockIndex = allocatedBlocks[i + 1];
				fat.setNextBlock(BlockIndex, nextBlockIndex);
			}
		}

		fat.createFile(fileName, allocatedBlocks[0]);
	}

	private void writeBlock(int blockNumber, byte[] data) throws IOException {
		long offset = (long) blockNumber * blockSize;
		partitionHead.seek(offset);
		partitionHead.write(data);
		usedSpace += offset;
		freeSpace -= offset;
	}

	private byte[] readBlock(int blockNumber) throws IOException {
		long offset = (long) blockNumber * blockSize;
		partitionHead.seek(offset);
		byte[] data = new byte[blockSize];
		partitionHead.read(data);
		return data;
	}

	public char getPartitionLabel() {
		return partitionLabel;
	}

	public long getPartitionSize() {
		return partitionSize;
	}

	public long getUsedSpace() {
		return usedSpace;
	}

	public long getFreeSpace() {
		return freeSpace;
	}
}
