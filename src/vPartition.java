import java.io.*;
import java.util.*;

public class vPartition implements Serializable {
	public static final int blockSize = 512;  // (4096 bits)
	public static final int bootSize = 1;
	private final char partitionLabel;
	private final UUID uuid;
	private final long partitionSize;
	private long usedSpace;
	private long freeSpace;
	private final int blocksPerFat = 255;
	transient private final RandomAccessFile partitionHead;
	transient private final FileAllocationTable fat;

	/**
	 * Loading existing vPartition constructor
	 * @param uuid_string: uuid of partition
	 * @throws FileNotFoundException: if partition was not found
	 */
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
				this.usedSpace = deserialized.usedSpace;
				this.freeSpace = deserialized.freeSpace;
			}

			this.fat = deserializeFat();

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creating new vPartition constructor
	 * @param driveLabel: a unique character that represents partition label
	 * @param partitionSize: size of partition in bytes
	 */
	public vPartition(char driveLabel, long partitionSize) throws Exception {
		this.uuid = UUID.randomUUID();
		String filePath = "%s.vpar".formatted(this.uuid);
		File file = new File(filePath);

		if (partitionSize <= 262144)
			throw new Exception("A partition must have at least 256KB of total space");
		try {
			this.partitionHead = new RandomAccessFile(file, "rw");
			this.partitionHead.setLength(partitionSize);
			this.partitionLabel = driveLabel >= 97 ? (char) (driveLabel - 32) : driveLabel;
			this.partitionSize = partitionSize;
			this.fat = new FileAllocationTable((int) Math.ceilDiv(partitionSize, blockSize) - firstDataBlock());
			this.usedSpace = (long) firstDataBlock() * blockSize;
			this.freeSpace = partitionSize - this.usedSpace;

			serializeFat();
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
				oos.writeObject(this);
				writeBlock(0, baos.toByteArray());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Serializes a FAT and allocates @blocksPerFat blocks.
	 */
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

	/**
	 * Deserializes a FAT and returns FileAllocationTable instance
	 */
	public FileAllocationTable deserializeFat() {
		byte[] obj = new byte[blockSize * blocksPerFat];

		try {
			for (int i = 0; i < blocksPerFat; i++) {
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

	/**
	 * creates a new vFile instance (Function will be modified once vFile class is ready)
	 * @param fileName: name of the file
	 */
	public void createFile(String fileName) {
		if (fat.getFileStartBlock(fileName) != null)
			return;
		fat.createEntry(fileName, null);
	}

	/**
	 * saves to a vFile instance (Function will be modified once vFile class is ready)
	 * @param fileName: name of the file
	 * @param data: byte array containing content of vFile
	 */
	public void saveFileData(String fileName, byte[] data) {
		if (fat.getFileStartBlock(fileName) != - 1) {
			deleteFileData(fileName);
			long n_blocks = fat.deleteEntry(fileName);
			usedSpace -= n_blocks * blockSize;
			freeSpace += n_blocks * blockSize;
		}
		if (data.length == 0)
			fat.createEntry(fileName, -1);
		int[] allocatedBlocks = new int[Math.ceilDiv(data.length, blockSize)];

		Arrays.setAll(allocatedBlocks, i -> (fat.allocateBlock() + firstDataBlock()));
		usedSpace += (long) allocatedBlocks.length * blockSize;
		freeSpace -= (long) allocatedBlocks.length * blockSize;

		try {
			for (int i = 0; i < allocatedBlocks.length; i++) {
				int BlockIndex = allocatedBlocks[i];
				byte[] BlockData = Arrays.copyOfRange(data, i * blockSize, (i + 1) * blockSize);
				writeBlock(BlockIndex, BlockData);

				if (i < allocatedBlocks.length - 1) {
					int nextBlockIndex = allocatedBlocks[i + 1];
					fat.setNextBlock(BlockIndex, nextBlockIndex);
				}
			}
		} catch (IOException e) {
				throw new RuntimeException(e);
		}

		fat.createEntry(fileName, allocatedBlocks[0]);
	}

	/**
	 * deletes file data
	 * @param fileName: name of the file
	 */
	public void deleteFileData(String fileName) {
		Integer idx = fat.getFileStartBlock(fileName);
		if (idx == -1)
			return;
		byte[] data = new byte[blockSize];
		try {
			while (idx != null) {
				writeBlock(firstDataBlock() + idx, data);
				idx = fat.getNextBlock(idx);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Writes data to specified block
	 * @param blockNumber: index of block
	 * @param data: byte array containing data
	 * @throws IOException: in case partition is inaccessible
	 */
	private void writeBlock(int blockNumber, byte[] data) throws IOException {
		long offset = (long) blockNumber * blockSize;
		partitionHead.seek(offset);
		partitionHead.write(data);
	}

	/**
	 * Reads data from specified block
	 * @param blockNumber: index of block
	 * @throws IOException: in case partition is inaccessible
	 */
	private byte[] readBlock(int blockNumber) throws IOException {
		long offset = (long) blockNumber * blockSize;
		partitionHead.seek(offset);
		byte[] data = new byte[blockSize];
		partitionHead.read(data);
		return data;
	}

	public int firstDataBlock() {
		return bootSize + blocksPerFat;
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

	public UUID getUuid() {
		return uuid;
	}

	@Override
	public String toString() {
		return "vPartition [" + partitionLabel +
				"]\nuuid = " + uuid +
				"\npartitionSize = " + partitionSize +
				" Bytes\nusedSpace = " + usedSpace +
				" Bytes\nfreeSpace = " + freeSpace +
				" Bytes\n";
	}
}
