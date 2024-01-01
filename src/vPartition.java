import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

public class vPartition implements Serializable {
	public static final int blockSize = 512;  // (4096 bits)
	public static final int bootSize = 1;
	private final char partitionLabel;
	private final UUID uuid;
	private final long partitionSize;
	private long usedSpace;
	private long freeSpace;
	private final int blocksPerFat = 191;
	private final int blocksPerRoot = 64;
	transient private final RandomAccessFile partitionHead;
	transient private final FileAllocationTable fat;
	transient private final vDirectory rootFolder;

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

			this.fat = new FileAllocationTable(deserializeFat());
			this.rootFolder = new vDirectory(deserializeRoot());
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
			this.rootFolder = new vDirectory("~", null);
			this.usedSpace = (long) firstDataBlock() * blockSize;
			this.freeSpace = partitionSize - this.usedSpace;

			save();

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void save() throws IOException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(this);
			writeBlock(0, baos.toByteArray());
		}

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(this.fat);

			byte[] serializedData = baos.toByteArray();

			for (int i = 0; i < Math.ceilDiv(serializedData.length, blockSize); i++) {
				byte[] chunk = new byte[blockSize];
				int startIdx = (i * blockSize);
				int endIdx = Math.min((i + 1) * blockSize, serializedData.length);
				System.arraycopy(serializedData, startIdx, chunk, 0, endIdx - startIdx);
				writeBlock(i + bootSize, chunk);
			}
		}

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(this.rootFolder);
			byte[] serializedData = baos.toByteArray();

			for (int i = 0; i < Math.ceilDiv(serializedData.length, blockSize); i++) {
				byte[] chunk = new byte[blockSize];
				int startIdx = (i * blockSize);
				int endIdx = Math.min((i + 1) * blockSize, serializedData.length);
				System.arraycopy(serializedData, startIdx, chunk, 0, endIdx - startIdx);
				writeBlock(i + bootSize + blocksPerFat, chunk);
			}
		}
	}

	/**
	 * Deserializes a FAT and returns FileAllocationTable instance
	 */
	public FileAllocationTable deserializeFat() {
		byte[] obj = new byte[blockSize * blocksPerFat];

		try {
			for (int i = 0; i < blocksPerFat; i++) {
				byte[] chunk = readBlock(i + bootSize);
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
	 * Deserializes a FAT and returns FileAllocationTable instance
	 */
	public vDirectory deserializeRoot() {
		byte[] obj = new byte[blockSize * blocksPerRoot];

		try {
			for (int i = 0; i < blocksPerRoot; i++) {
				byte[] chunk = readBlock(i + bootSize + blocksPerFat);
				System.arraycopy(chunk, 0, obj, i * blockSize, chunk.length);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try (ByteArrayInputStream bais = new ByteArrayInputStream(obj);
		     ObjectInputStream ois = new ObjectInputStream(bais)) {
			return (vDirectory) ois.readObject();
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a new vFile instance (Function will be modified once vFile class is ready)
	 */
	public vFile createFile(vDirectory directory, String fileName, String fileType) {
		vFile file = new vFile(fileName, fileType, directory);
		if (directory.getFileStartBlock(file) != null)
			return null;
		directory.createEntry(file, -1);
		return file;
	}

	public void deleteFile(vDirectory directory, String fileName, String fileType) {
		vFile file = directory.getFileByNameAndType(fileName, fileType);
		if (file == null)
			throw new IllegalArgumentException("File doesn't exists");

		deleteFileData(directory, file);
		directory.deleteEntry(file);
	}

	public void moveFile(vDirectory sourceDir, vFile sourceFile, vDirectory destDir, String destFile) {
		Integer startBlock = sourceDir.getFileStartBlock(sourceFile);
		if (startBlock == null)
			throw new IllegalArgumentException("File not found");

		String[] arr = destFile.split("\\.", 2);
		sourceDir.deleteEntry(sourceFile);
		sourceFile.setName(arr[0]);
		sourceFile.setType(arr[1]);
		sourceFile.setLocation(destDir);
		destDir.createEntry(sourceFile, sourceFile.getStartBlock());
	}

	public void copyFile(vDirectory sourceDir, vFile sourceFile, vDirectory destDir, String destFile) {
		if (sourceFile == null)
			throw new IllegalArgumentException("File doesn't exists");

		byte[] data = getFileData(sourceDir, sourceFile);
		String[] arr = destFile.split("\\.", 2);
		saveFileData(destDir, createFile(destDir, arr[0], arr[1]), data);
	}
	public void createFolder(vDirectory parent, String folderName) {
		vDirectory folder = new vDirectory(folderName, parent);
		if (parent.getFileStartBlock(folder) != null)
			return;
		parent.createEntry(folder, -1);
	}

	public void deleteFolder(vDirectory parent, String folderName) {
		vDirectory folder = parent.getSubFolderByName(folderName);
		if (folder == null)
			throw new IllegalArgumentException("Folder doesn't exists");
		parent.deleteEntry(folder);
	}

	/**
	 * Retrieves data from a vFile instance
	 * @param directory: vDirectory instance
	 * @param file: vFile instance
	 * @return byte array containing content of the vFile
	 */
	public byte[] getFileData(vDirectory directory, vFile file) {
		try {
			Integer idx = directory.getFileStartBlock(file);
			if (idx == null) {
				throw new RuntimeException("File not found.");
			}

			if (idx == -1)
				return new byte[0];

			file.setAccessTime(LocalDateTime.now());
			List<byte[]> blocks = new LinkedList<>();
			while (idx != -1) {
				byte[] blockData = readBlock(firstDataBlock() + idx);
				blocks.add(blockData);
				idx = fat.getNextBlock(idx);
			}

			int totalSize = blocks.size() * blockSize;
			byte[] result = new byte[totalSize];

			int offset = 0;
			for (int i = 0; i < blocks.size(); i++) {
				byte[] block = blocks.get(i);
				System.arraycopy(block, 0, result, offset, blockSize);
				offset += blockSize;
			}
			return result;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Saves to a vFile instance (Function will be modified once vFile class is ready)
	 * @param file: vFile instance
	 * @param data: byte array containing content of vFile
	 */
	public void saveFileData(vDirectory directory, vFile file, byte[] data) {
		if (directory.getFileStartBlock(file) != - 1) {
			long n_blocks = deleteFileData(directory, file);
			directory.deleteEntry(file);
			usedSpace -= n_blocks * blockSize;
			freeSpace += n_blocks * blockSize;
		}

		if (data.length == 0)
			directory.createEntry(file, -1);
		int[] allocatedBlocks = new int[Math.ceilDiv(data.length, blockSize)];
		file.setSize(data.length);
		file.setNumOfBlocks(allocatedBlocks.length);
		Arrays.setAll(allocatedBlocks, i -> fat.allocateBlock());
		usedSpace += (long) allocatedBlocks.length * blockSize;
		freeSpace -= (long) allocatedBlocks.length * blockSize;

		file.setModificationTime(LocalDateTime.now());

		try {
			for (int i = 0; i < allocatedBlocks.length; i++) {
				int BlockIndex = allocatedBlocks[i];
				byte[] BlockData = Arrays.copyOfRange(data, i * blockSize, (i + 1) * blockSize);
				writeBlock(BlockIndex + firstDataBlock(), BlockData);

				if (i < allocatedBlocks.length - 1) {
					int nextBlockIndex = allocatedBlocks[i + 1];
					fat.setNextBlock(BlockIndex, nextBlockIndex);
				}
			}
		} catch (IOException e) {
				throw new RuntimeException(e);
		}

		directory.createEntry(file, allocatedBlocks[0]);
	}

	/**
	 * Deletes file data
	 * @param file: vFile instance
	 */
	public int deleteFileData(vDirectory directory, vFile file) {
		Integer idx = directory.getFileStartBlock(file);
		if (idx == null)
			throw new RuntimeException("File not found.");
		if (idx == -1)
			return 0;
		byte[] data = new byte[blockSize];
		int counter = 0;
		try {
			while (idx != -1) {
				System.out.println(idx);
				writeBlock(firstDataBlock() + idx, data);
				idx = fat.getNextBlock(idx);
				fat.deallocateBlock(idx);
				counter += blockSize;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return counter;
	}

	public vDirectory getDirectoryByPath(String path) {
		if (!path.matches("^(?i)%c:/.*$".formatted(partitionLabel))) {
			throw new IllegalArgumentException("Invalid path format");
		}

		String[] pathElements = path.substring(3).split("/");

		vDirectory currentDirectory = rootFolder;

		for (String dirName : pathElements) {
			if (dirName.isEmpty())
				continue;

			currentDirectory = currentDirectory.getSubFolderByName(dirName);
		}

		return currentDirectory;
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
		return bootSize + blocksPerFat + blocksPerRoot;
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

	public vDirectory getRoot() {
		return rootFolder;
	}

	public String getPathString(vDirectory folder) {
		StringBuilder path = new StringBuilder();
		while (folder.getLocation() != null)
		{
			path.insert(0, folder.getName());
			path.insert(0, "\\");
			folder = folder.getLocation();
		}
		path.insert(0, "%c:".formatted(this.partitionLabel));
		return path.toString();
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
