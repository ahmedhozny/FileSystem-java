import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a virtual partition with a file system.
 */
public class vPartition implements Serializable {
	public static final int blockSize = 512;  // (4096 bits)
	public static final int bootSize = 1;
	private final char partitionLabel;
	private final UUID uuid;
	private final long partitionSize;
	private long usedSpace;
	private long freeSpace;
	private final int blocksPerFat = 191;  // Number of blocks for the File Allocation Table
	private final int blocksPerRoot = 64;  // Number of blocks for the root directory
	transient private final RandomAccessFile partitionHead;
	transient private final FileAllocationTable fat;
	transient private final vDirectory rootFolder;

	/**
	 * Constructor for loading an existing vPartition.
	 *
	 * @param uuid_string UUID of the partition
	 * @throws FileNotFoundException if the partition file is not found
	 */
	public vPartition(String uuid_string) throws IOException, ClassNotFoundException {
		String filePath = "%s.vpar".formatted(uuid_string);
		File file = new File(filePath);
		if (!file.isFile())
			throw new FileNotFoundException(filePath);
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

	}

	/**
	 * Constructor for creating a new vPartition.
	 *
	 * @param driveLabel Unique character representing the partition label
	 * @param partitionSize Size of the partition in bytes
	 */
	public vPartition(char driveLabel, long partitionSize) throws Exception {
		this.uuid = UUID.randomUUID();
		String filePath = "%s.vpar".formatted(this.uuid);
		File file = new File(filePath);

		if (partitionSize <= 262144)
			throw new Exception("A partition must have at least 256KB of total space");
		this.partitionHead = new RandomAccessFile(file, "rw");
		this.partitionHead.setLength(partitionSize);
		this.partitionLabel = driveLabel >= 97 ? (char) (driveLabel - 32) : driveLabel;
		this.partitionSize = partitionSize;
		this.fat = new FileAllocationTable((int) Math.ceilDiv(partitionSize, blockSize) - firstDataBlock());
		this.rootFolder = new vDirectory("~", null);
		this.usedSpace = (long) firstDataBlock() * blockSize;
		this.freeSpace = partitionSize - this.usedSpace;

		save();
	}

	/**
	 * Saves the current state of vPartition to the disk.
	 *
	 * @throws IOException: if an error occurs during serialization or writing to the disk
	 */
	public void save() throws IOException {
		// Serialize and save vPartition
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(this);
			writeBlock(0, baos.toByteArray());
		}

		// Serialize and save File Allocation Table (FAT)
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(this.fat);

			byte[] serializedData = baos.toByteArray();

			// Write FAT to multiple blocks
			for (int i = 0; i < Math.ceilDiv(serializedData.length, blockSize); i++) {
				byte[] chunk = new byte[blockSize];
				int startIdx = (i * blockSize);
				int endIdx = Math.min((i + 1) * blockSize, serializedData.length);
				System.arraycopy(serializedData, startIdx, chunk, 0, endIdx - startIdx);
				writeBlock(i + bootSize, chunk);
			}
		}

		// Serialize and save root folder
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(this.rootFolder);
			byte[] serializedData = baos.toByteArray();

			// Write root folder to multiple blocks
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
	 * Deserialize the FAT from the disk.
	 *
	 * @return FileAllocationTable instance
	 */
	public FileAllocationTable deserializeFat() throws IOException, ClassNotFoundException {
		byte[] obj = new byte[blockSize * blocksPerFat];
		for (int i = 0; i < blocksPerFat; i++) {
			byte[] chunk = readBlock(i + bootSize);
			System.arraycopy(chunk, 0, obj, i * blockSize, chunk.length);
		}

		try (ByteArrayInputStream bais = new ByteArrayInputStream(obj);
		     ObjectInputStream ois = new ObjectInputStream(bais)) {
			return (FileAllocationTable) ois.readObject();
		}
	}


	/**
	 * Deserialize the root directory from the disk.
	 *
	 * @return vDirectory instance
	 */
	public vDirectory deserializeRoot() throws IOException, ClassNotFoundException {
		byte[] obj = new byte[blockSize * blocksPerRoot];

		for (int i = 0; i < blocksPerRoot; i++) {
			byte[] chunk = readBlock(i + bootSize + blocksPerFat);
			System.arraycopy(chunk, 0, obj, i * blockSize, chunk.length);
		}

		try (ByteArrayInputStream bais = new ByteArrayInputStream(obj);
		     ObjectInputStream ois = new ObjectInputStream(bais)) {
			return (vDirectory) ois.readObject();
		}
	}

	public void forceUnmount() {
		try {
			this.partitionHead.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	/**
	 * Creates a new vFile instance
	 */
	public vFile createFile(vDirectory directory, String fileName, String fileType) {
		vFile file = new vFile(fileName, fileType, directory);
		if (directory.getFileStartBlock(file) != null)
			return null;
		directory.createEntry(file, -1);
		return file;
	}

	/**
	 * Deletes a file from the specified directory.
	 *
	 * @param directory:  vDirectory instance
	 * @param file:  file to be deleted
	 * @throws IllegalArgumentException if the file doesn't exist
	 */
	public void deleteFile(vDirectory directory, vFile file) {
		if (file == null)
			throw new IllegalArgumentException("File doesn't exists");
		long n_blocks = deleteFileData(directory, file);
		directory.deleteEntry(file);
		usedSpace -= n_blocks * blockSize;
		freeSpace += n_blocks * blockSize;
	}

	/**
	 * Moves a file from the source directory to the destination directory.
	 *
	 * @param sourceDir:  source vDirectory instance
	 * @param sourceFile: file to be moved
	 * @param destDir:    destination vDirectory instance
	 * @param destFile:   new name of the file after moving
	 * @throws IllegalArgumentException if the file is not found in the source directory
	 */
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

	/**
	 * Copies a file from the source directory to the destination directory.
	 *
	 * @param sourceDir:  source vDirectory instance
	 * @param sourceFile: file to be copied
	 * @param destDir:    destination vDirectory instance
	 * @param destFile:   name of the new file after copying
	 * @throws IllegalArgumentException if the source file is not found
	 */
	public void copyFile(vDirectory sourceDir, vFile sourceFile, vDirectory destDir, String destFile) {
		if (sourceFile == null)
			throw new IllegalArgumentException("File doesn't exists");

		byte[] data = getFileData(sourceDir, sourceFile);
		if (destFile.isEmpty())
			destFile = sourceFile.getFullName();
		String[] arr = destFile.split("\\.", 2);
		saveFileData(destDir, createFile(destDir, arr[0], arr[1]), data);
	}

	/**
	 * Creates a new folder in the specified parent directory.
	 *
	 * @param parent:     parent vDirectory instance
	 * @param folderName: name of the new folder
	 */
	public void createFolder(vDirectory parent, String folderName) {
		vDirectory folder = new vDirectory(folderName, parent);
		if (parent.getFileStartBlock(folder) != null)
			return;
		parent.createEntry(folder, -1);
	}

	/**
	 * Deletes a folder from the specified parent directory.
	 *
	 * @param parent:     parent vDirectory instance
	 * @param folderName: name of the folder to be deleted
	 * @throws IllegalArgumentException if the folder doesn't exist
	 */
	public void deleteFolder(vDirectory parent, String folderName) {
		vDirectory folder = parent.getSubFolderByName(folderName);
		if (folder == null)
			throw new IllegalArgumentException("Folder doesn't exists");
		parent.deleteEntry(folder);
	}

	/**
	 * Retrieves data of a vFile
	 * @param directory: vDirectory instance
	 * @param file: vFile instance
	 * @return byte array containing content of the vFile
	 */
	public byte[] getFileData(vDirectory directory, vFile file) {
		if (!file.hasReadPermission())
			throw new SecurityException("File is read protected");
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
			for (byte[] block : blocks) {
				System.arraycopy(block, 0, result, offset, blockSize);
				offset += blockSize;
			}
			int i;
			for (i = 0; i < result.length; i++) {
				if (result[i] == 0)
					break;
			}
			result = Arrays.copyOfRange(result, 0, i);
			return result;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Saves data to a vFile instance
	 *
	 * @param directory: vDirectory instance
	 * @param file:      vFile instance
	 * @param data:      byte array containing content of vFile
	 */
	public void saveFileData(vDirectory directory, vFile file, byte[] data) {
		if (!file.hasWritePermission())
			throw new SecurityException("File is write protected.");
		if (directory.getFileStartBlock(file) != - 1) {
			long n_blocks = deleteFileData(directory, file);
			directory.deleteEntry(file);
			usedSpace -= n_blocks * blockSize;
			freeSpace += n_blocks * blockSize;
		}

		if (data.length == 0) {
			directory.createEntry(file, -1);
			return;
		}
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
	 * Deletes file data associated with a vFile in the specified directory.
	 *
	 * @param directory The vDirectory instance containing the file.
	 * @param file The vFile instance whose data needs to be deleted.
	 * @return The total number of bytes freed by deleting the file data.
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
				writeBlock(firstDataBlock() + idx, data);
				int next = fat.getNextBlock(idx);
				fat.deallocateBlock(idx);
				idx = next;
				counter++;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return counter;
	}

	/**
	 * Retrieves the vDirectory instance corresponding to the specified path.
	 * The path should be in the format "%c:/folder1/folder2/.../folderN".
	 *
	 * @param path The full path of the directory, including the partition label.
	 * @return The vDirectory instance corresponding to the specified path.
	 */
	public vDirectory getDirectoryByPath(String path) {
		if (!path.matches("^/.*$")) {
			return null;
		}

		String[] pathElements = path.split("/");

		vDirectory currentDirectory = rootFolder;

		for (String dirName : pathElements) {
			if (dirName.isEmpty())
				continue;

			currentDirectory = currentDirectory.getSubFolderByName(dirName);
		}

		return currentDirectory;
	}

	/**
	 * Writes data to the specified block in the vPartition's storage.
	 *
	 * @param blockNumber The index of the block to write.
	 * @param data The byte array containing data to be written to the block.
	 * @throws IOException If there is an issue accessing the partition.
	 */
	private void writeBlock(int blockNumber, byte[] data) throws IOException {
		long offset = (long) blockNumber * blockSize;
		partitionHead.seek(offset);
		partitionHead.write(data);
	}

	/**
	 * Reads data from the specified block in the vPartition's storage.
	 *
	 * @param blockNumber The index of the block to read.
	 * @return A byte array containing the data read from the block.
	 * @throws IOException If there is an issue accessing the partition.
	 */
	private byte[] readBlock(int blockNumber) throws IOException {
		long offset = (long) blockNumber * blockSize;
		partitionHead.seek(offset);
		byte[] data = new byte[blockSize];
		partitionHead.read(data);
		return data;
	}

	/**
	 * Constructs and returns the full path string of a vDirectory instance.
	 * The path format is "%c:/folder1/folder2/.../folderN".
	 *
	 * @param folder The vDirectory instance for which to generate the path.
	 * @return The full path string of the specified vDirectory.
	 */
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
				"]\nUUID = " + uuid +
				"\nPartition Size = " + partitionSize +
				" Bytes\nTotal Used Space = " + usedSpace +
				" Bytes\nUsed Space (System excluded) = " + (usedSpace - ((long) firstDataBlock() * blockSize)) +
				" Bytes\nfreeSpace = " + freeSpace +
				" Bytes\n";
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
}
