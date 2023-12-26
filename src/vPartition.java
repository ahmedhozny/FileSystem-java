import java.io.*;
import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.UUID;

public class vPartition implements Serializable {
	public static final int blockSize = 4096;  // 4096 bits (512 Bytes)
	private final char partitionLabel;
	private final UUID uuid;
	private final long partitionSize;
	private long usedSpace;
	private long freeSpace;
	transient private final RandomAccessFile partitionHead;
	private final ArrayList<Integer> files_blocks;

	public vPartition(String uuid_string) throws FileNotFoundException {
		String filePath = "%s.vpar".formatted(uuid_string);
		File file = new File(filePath);
		if (!file.isFile())
			throw new FileNotFoundException(filePath);
		try {
			this.partitionHead = new RandomAccessFile(file, "rw");

			try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(readBlock(0)))) {
				// Deserialize the object from the first block
				vPartition deserialized = (vPartition) ois.readObject();
				this.uuid = deserialized.uuid;
				this.partitionLabel = deserialized.partitionLabel;
				this.partitionSize = deserialized.partitionSize;
				this.files_blocks = deserialized.files_blocks;
			}
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
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		this.partitionLabel = driveLabel;
		this.partitionSize = partitionSize;
		this.files_blocks = new ArrayList<>();

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(this);
			writeBlock(0, baos.toByteArray());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		System.out.println("LOL");
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
