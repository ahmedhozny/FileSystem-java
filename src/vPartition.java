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
	private final int blocksPerRoot = 64;  // Number of blocks for the root folder
	transient private final RandomAccessFile partitionHead;
	transient private final FileAllocationTable fat;
	transient private final vFolder rootFolder;

	/**
	 * Constructor for loading an existing vPartition.
	 *
	 * @param uuid_string UUID of the partition
	 * @throws IOException if an I/O error occurs during file operations
	 * @throws ClassNotFoundException if the class of a serialized object cannot be found
	 * @throws FileNotFoundException if the partition file is not found
	 */
	public vPartition(String uuid_string) throws IOException, ClassNotFoundException {
		// Build the file path based on the UUID
		String filePath = "%s.vpar".formatted(uuid_string);
		File file = new File(filePath);

		// Check if the file exists; throw an exception if not found
		if (!file.isFile())
			throw new FileNotFoundException(filePath);

		// Open a RandomAccessFile for reading and writing the partition file
		this.partitionHead = new RandomAccessFile(file, "rw");

		// Deserialize the vPartition object from the first block of the partition
		try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(readBlock(0)))) {
			vPartition deserialized = (vPartition) ois.readObject();

			// Copy relevant fields from the deserialized object
			this.uuid = deserialized.uuid;
			this.partitionLabel = deserialized.partitionLabel;
			this.partitionSize = deserialized.partitionSize;
			this.usedSpace = deserialized.usedSpace;
			this.freeSpace = deserialized.freeSpace;
		}

		// Deserialize the File Allocation Table (FAT) and the root folder
		this.fat = new FileAllocationTable(deserializeFat());
		this.rootFolder = new vFolder(deserializeRoot());
	}

	/**
	 * Constructor for creating a new vPartition.
	 *
	 * @param driveLabel Unique character representing the partition label
	 * @param partitionSize Size of the partition in bytes
	 * @throws Exception if an error occurs during partition creation
	 */
	public vPartition(char driveLabel, long partitionSize) throws Exception {
		// Generate a random UUID for the partition
		this.uuid = UUID.randomUUID();
		String filePath = "%s.vpar".formatted(this.uuid);
		File file = new File(filePath);

		// Check if the partition size is at least 256KB
		if (partitionSize <= 262144)
			throw new Exception("A partition must have at least 256KB of total space");

		// Initialize a RandomAccessFile for reading and writing the partition file
		this.partitionHead = new RandomAccessFile(file, "rw");
		this.partitionHead.setLength(partitionSize);

		// Set partition label and size
		this.partitionLabel = driveLabel >= 97 ? (char) (driveLabel - 32) : driveLabel;
		this.partitionSize = partitionSize;

		// Initialize File Allocation Table (FAT) and root folder
		this.fat = new FileAllocationTable((int) Math.ceilDiv(partitionSize, blockSize) - firstDataBlock());
		this.rootFolder = new vFolder("~", null);

		// Calculate and set used and free space
		this.usedSpace = (long) firstDataBlock() * blockSize;
		this.freeSpace = partitionSize - this.usedSpace;

		// Save the newly created partition
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
	 * Deserialize the File Allocation Table (FAT) from the disk.
	 *
	 * @return The deserialized FileAllocationTable instance representing the FAT.
	 * @throws IOException If an I/O error occurs during the deserialization process.
	 * @throws ClassNotFoundException If the class of a serialized object cannot be found.
	 */
	public FileAllocationTable deserializeFat() throws IOException, ClassNotFoundException {
		// Create a byte array to store the serialized data of the FAT
		byte[] obj = new byte[blockSize * blocksPerFat];

		// Read serialized data from blocks and concatenate into the byte array
		for (int i = 0; i < blocksPerFat; i++) {
			byte[] chunk = readBlock(i + bootSize);
			System.arraycopy(chunk, 0, obj, i * blockSize, chunk.length);
		}

		// Deserialize the FileAllocationTable instance from the byte array
		try (ByteArrayInputStream bais = new ByteArrayInputStream(obj);
		     ObjectInputStream ois = new ObjectInputStream(bais)) {
			return (FileAllocationTable) ois.readObject();
		}
	}

	/**
	 * Deserialize the root folder from the disk.
	 *
	 * @return The deserialized vFolder instance representing the root folder.
	 * @throws IOException If an I/O error occurs during the deserialization process.
	 * @throws ClassNotFoundException If the class of a serialized object cannot be found.
	 */
	public vFolder deserializeRoot() throws IOException, ClassNotFoundException {
		// Create a byte array to store the serialized data of the root folder
		byte[] obj = new byte[blockSize * blocksPerRoot];

		// Read serialized data from blocks and concatenate into the byte array
		for (int i = 0; i < blocksPerRoot; i++) {
			byte[] chunk = readBlock(i + bootSize + blocksPerFat);
			System.arraycopy(chunk, 0, obj, i * blockSize, chunk.length);
		}

		// Deserialize the vFolder instance from the byte array
		try (ByteArrayInputStream bais = new ByteArrayInputStream(obj);
		     ObjectInputStream ois = new ObjectInputStream(bais)) {
			return (vFolder) ois.readObject();
		}
	}

	/**
	 * Forces an unmount operation by closing the RandomAccessFile associated with the virtual partition.
	 */
	public void forceUnmount() {
		try {
			this.partitionHead.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a new vFile instance in the specified folder.
	 *
	 * @param folder The parent vFolder instance.
	 * @param fileName The name of the new file.
	 * @param fileType The type (extension) of the new file.
	 * @return The newly created vFile instance, or null if the file already exists in the folder.
	 */
	public vFile createFile(vFolder folder, String fileName, String fileType) {
		// Create a new vFile instance
		vFile file = new vFile(fileName, fileType, folder);

		// Check if the file already exists in the folder
		if (folder.getFileStartBlock(file) != null)
			return null; // File with the same name and type already exists

		// Create a new entry for the file in the folder
		folder.createEntry(file, -1);

		// Return the newly created vFile instance
		return file;
	}

	/**
	 * Deletes a file from the specified folder.
	 *
	 * @param folder:  vFolder instance
	 * @param file:  file to be deleted
	 * @throws IllegalArgumentException if the file doesn't exist
	 */
	public void deleteFile(vFolder folder, vFile file) {
		if (file == null)
			throw new IllegalArgumentException("File doesn't exists");
		long n_blocks = deleteFileData(folder, file);
		folder.deleteEntry(file);
		usedSpace -= n_blocks * blockSize;
		freeSpace += n_blocks * blockSize;
	}

	/**
	 * Moves a file from the source folder to the destination folder.
	 *
	 * @param sourceDir The source vFolder instance.
	 * @param sourceFile The file to be moved (vFile instance).
	 * @param destDir The destination vFolder instance.
	 * @param destFile The new name of the file after moving.
	 * @throws IllegalArgumentException If the file is not found in the source folder.
	 */
	public void moveFile(vFolder sourceDir, vFile sourceFile, vFolder destDir, String destFile) {
		// Check if the file exists in the source folder
		Integer startBlock = sourceDir.getFileStartBlock(sourceFile);
		if (startBlock == null) {
			throw new IllegalArgumentException("File not found");
		}

		// If destFile is empty, use the original full name of the source file
		if (destFile.isEmpty()) {
			destFile = sourceFile.getFullName();
		}

		// Split destFile into name and type
		String[] arr = destFile.split("\\.", 2);

		// Update file properties
		sourceFile.setName(arr[0]);
		sourceFile.setType(arr[1]);
		sourceFile.setLocation(destDir);

		// Delete the file entry from the source folder
		sourceDir.deleteEntry(sourceFile);

		// Create a new entry for the file in the destination folder
		destDir.createEntry(sourceFile, sourceFile.getStartBlock());
	}

	/**
	 * Copies a file from the source folder to the destination folder.
	 *
	 * @param sourceDir:  source vFolder instance
	 * @param sourceFile: file to be copied
	 * @param destDir:    destination vFolder instance
	 * @param destFile:   name of the new file after copying
	 * @throws IllegalArgumentException if the source file is not found
	 */
	public void copyFile(vFolder sourceDir, vFile sourceFile, vFolder destDir, String destFile) {
		if (sourceFile == null)
			throw new IllegalArgumentException("File doesn't exists");

		byte[] data = getFileData(sourceDir, sourceFile);
		if (destFile.isEmpty())
			destFile = sourceFile.getFullName();
		String[] arr = destFile.split("\\.", 2);
		saveFileData(destDir, createFile(destDir, arr[0], arr[1]), data);
	}

	/**
	 * Creates a new folder in the specified parent folder.
	 *
	 * @param parent The parent vFolder instance.
	 * @param folderName The name of the new folder.
	 */
	public void createFolder(vFolder parent, String folderName) {
		// Create a new vFolder object with the given name and parent
		vFolder folder = new vFolder(folderName, parent);

		// Check if the folder already exists in the parent folder
		if (parent.getFileStartBlock(folder) != null) {
			return; // Folder already exists, no need to create a new one
		}

		// Create a new entry for the folder in the parent folder
		parent.createEntry(folder, -1);
	}


	/**
	 * Deletes a folder from the specified parent folder.
	 *
	 * @param parent The parent vFolder instance.
	 * @param folderName The name of the folder to be deleted.
	 * @throws IllegalArgumentException If the folder doesn't exist.
	 */
	public void deleteFolder(vFolder parent, String folderName) {
		// Get the folder to be deleted
		vFolder folder = parent.getSubFolderByName(folderName);

		// Check if the folder exists
		if (folder == null)
			throw new IllegalArgumentException("Folder doesn't exist");

		// Recursively delete the folder and its children
		deleteFolderChildren(folder);

		// Remove the folder entry from the parent folder
		parent.deleteEntry(folder);
	}

	/**
	 * Retrieves the content of a vFile from the specified vFolder.
	 *
	 * @param folder The vFolder instance containing the file.
	 * @param file The vFile instance for which to retrieve the data.
	 * @return A byte array containing the content of the vFile.
	 * @throws SecurityException If the file is read-protected.
	 * @throws RuntimeException If the file is not found or an IO error occurs during data retrieval.
	 */
	public byte[] getFileData(vFolder folder, vFile file) {
		// Check read permission for the file
		if (!file.hasReadPermission()) {
			throw new SecurityException("File is read-protected");
		}

		try {
			// Get the start block index of the file
			Integer idx = folder.getFileStartBlock(file);

			// Check if the file is not found
			if (idx == null) {
				throw new RuntimeException("File not found.");
			}

			// Check if the file has no data blocks
			if (idx == -1) {
				return new byte[0]; // Empty file
			}

			// Update the access time of the file
			file.setAccessTime(LocalDateTime.now());

			// List to store data blocks of the file
			List<byte[]> blocks = new LinkedList<>();

			// Iterate through the file's data blocks
			while (idx != -1) {
				// Read the data block from the partition
				byte[] blockData = readBlock(firstDataBlock() + idx);

				// Add the data block to the list
				blocks.add(blockData);

				// Get the next data block index
				idx = fat.getNextBlock(idx);
			}

			// Calculate the total size of the file's content
			int totalSize = blocks.size() * blockSize;

			// Create a byte array to store the concatenated content of the file
			byte[] result = new byte[totalSize];

			// Copy data from each block to the result array
			int offset = 0;
			for (byte[] block : blocks) {
				System.arraycopy(block, 0, result, offset, blockSize);
				offset += blockSize;
			}

			// Find the actual size of the file's content by searching for the first zero byte
			int i;
			for (i = 0; i < result.length; i++) {
				if (result[i] == 0) {
					break;
				}
			}

			// Trim the result array to the actual size of the file's content
			result = Arrays.copyOfRange(result, 0, i);

			return result;
		} catch (IOException e) {
			// Throw a runtime exception if an IO error occurs during data retrieval
			throw new RuntimeException("Error retrieving file data.", e);
		}
	}

	/**
	 * Saves data to a vFile instance within the specified vFolder.
	 *
	 * @param folder The vFolder instance containing the file.
	 * @param file The vFile instance for which to save the data.
	 * @param data The byte array containing the content of the vFile.
	 * @throws SecurityException If the file is write-protected.
	 * @throws RuntimeException If an error occurs during data saving.
	 */
	public void saveFileData(vFolder folder, vFile file, byte[] data) {
		// Check write permission for the file
		if (!file.hasWritePermission())
			throw new SecurityException("File is write-protected.");

		// Check if the file already has allocated data blocks
		if (folder.getFileStartBlock(file) != -1) {
			// Delete existing data blocks and update space information
			long n_blocks = deleteFileData(folder, file);
			folder.deleteEntry(file);
			usedSpace -= n_blocks * blockSize;
			freeSpace += n_blocks * blockSize;
		}

		// Check if the data is empty, create an entry with no allocated blocks
		if (data.length == 0) {
			folder.createEntry(file, -1);
			return;
		}

		// Calculate the number of blocks needed for the data
		int[] allocatedBlocks = new int[Math.ceilDiv(data.length, blockSize)];

		// Update file metadata with size and block information
		file.setSize(data.length);
		file.setNumOfBlocks(allocatedBlocks.length);

		// Allocate data blocks using the File Allocation Table
		Arrays.setAll(allocatedBlocks, i -> fat.allocateBlock());

		// Update used and free space information
		usedSpace += (long) allocatedBlocks.length * blockSize;
		freeSpace -= (long) allocatedBlocks.length * blockSize;

		// Update modification time for the file
		file.setModificationTime(LocalDateTime.now());

		try {
			// Write data blocks to the partition
			for (int i = 0; i < allocatedBlocks.length; i++) {
				int blockIndex = allocatedBlocks[i];
				byte[] blockData = Arrays.copyOfRange(data, i * blockSize, (i + 1) * blockSize);

				// Write block data to the partition
				writeBlock(blockIndex + firstDataBlock(), blockData);

				// Update File Allocation Table with the next block index
				if (i < allocatedBlocks.length - 1) {
					int nextBlockIndex = allocatedBlocks[i + 1];
					fat.setNextBlock(blockIndex, nextBlockIndex);
				}
			}
		} catch (IOException e) {
			// Throw a runtime exception if an IO error occurs during data saving
			throw new RuntimeException("Error saving file data.", e);
		}

		// Create an entry in the folder with the index of the first data block
		folder.createEntry(file, allocatedBlocks[0]);
	}

	/**
	 * Deletes the file data associated with a vFile in the specified folder.
	 *
	 * @param folder The vFolder instance containing the file.
	 * @param file The vFile instance whose data needs to be deleted.
	 * @return The total number of bytes freed by deleting the file data.
	 * @throws RuntimeException If the file is not found or an IO error occurs during deletion.
	 */
	public int deleteFileData(vFolder folder, vFile file) {
		// Get the start block index of the file
		Integer idx = folder.getFileStartBlock(file);

		// Check if the file is not found
		if (idx == null)
			throw new RuntimeException("File not found.");
		// Check if the file has no data blocks
		if (idx == -1)
			return 0;

		// Create an empty block of data
		byte[] data = new byte[blockSize];

		int counter = 0;
		try {
			while (idx != -1) {
				// Write an empty block to the data block's location
				writeBlock(firstDataBlock() + idx, data);
				// Get the next data block index
				int next = fat.getNextBlock(idx);
				// Deallocate the current data block
				fat.deallocateBlock(idx);
				// Move to the next data block
				idx = next;
				// Increment the counter for each deleted data block
				counter++;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return counter;
	}

	/**
	 * Recursively deletes all files and subdirectories within the specified folder.
	 * This method is typically used when deleting a folder, and it ensures that all
	 * resources within the folder are properly released.
	 *
	 * @param folder The vFolder instance representing the folder to delete.
	 * @throws IllegalArgumentException If the specified folder does not exist.
	 */
	public void deleteFolderChildren(vFolder folder) {
		// Create a copy of the list to avoid ConcurrentModificationException
		List<vFile> filesCopy = new ArrayList<>(folder.getFiles());

		// Iterate over the copied list
		for (vFile file : filesCopy) {
			if (file instanceof vFolder) {
				deleteFolderChildren((vFolder) file);
				folder.deleteEntry(file);
			} else {
				deleteFile(folder, file);
			}
		}
	}

	/**
	 * Retrieves the vFolder instance corresponding to the specified path.
	 * The path should be in the format "%c:/folder1/folder2/.../folderN".
	 *
	 * @param path The full path of the folder, including the partition label.
	 * @return The vFolder instance corresponding to the specified path.
	 *         Returns null if the path is invalid or the folder does not exist.
	 */
	public vFolder getFolderByPath(String path) {
		// Check if the path starts with '/'
		if (!path.matches("^/.*$")) {
			return null; // Invalid path format
		}

		// Split the path into individual folder names
		String[] pathElements = path.split("/");

		// Start from the root folder
		vFolder currentFolder = rootFolder;

		// Iterate through each folder name in the path
		for (String dirName : pathElements) {
			// Skip empty folder names
			if (dirName.isEmpty()) {
				continue;
			}

			// Navigate to the next subFolder
			currentFolder = currentFolder.getSubFolderByName(dirName);

			// Check if the subFolder exists
			if (currentFolder == null) {
				return null; // folder does not exist
			}
		}

		return currentFolder;
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
	 * Constructs and returns the full path string of a vFolder instance.
	 * The path format is "%c:/folder1/folder2/.../folderN".
	 *
	 * @param folder The vFolder instance for which to generate the path.
	 * @return The full path string of the specified vFolder.
	 */
	public String getPathString(vFolder folder) {
		StringBuilder path = new StringBuilder();

		// Iterate through parent directories and prepend each folder name
		while (folder.getLocation() != null) {
			path.insert(0, folder.getName());
			path.insert(0, "\\");
			folder = folder.getLocation();
		}

		// Prepend the partition label and format the path
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

	public UUID getUuid() {
		return uuid;
	}

	public vFolder getRoot() {
		return rootFolder;
	}
}
