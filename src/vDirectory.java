import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Objects;

/**
 * Represents a virtual directory in the file system.
 */
public class vDirectory extends vFile implements Serializable {
	private final HashSet<vFile> files;
	/**
	 * Constructor for creating a new vDirectory.
	 * @param name Name of the directory
	 * @param location Parent directory (null for the root directory)
	 */
	public vDirectory(String name, vDirectory location) {
		super(name, null, location);
		this.files = new HashSet<>();
	}

	/**
	 * Constructor for creating a new vDirectory by copying an existing one.
	 * @param directory Existing vDirectory to copy
	 */
	public vDirectory(vDirectory directory) {
		super(directory.getName(), null, directory.getLocation());
		this.files = directory.files;
	}

	/**
	 * Creates a new entry (file or subdirectory) in the directory.
	 * @param file vFile instance representing the new entry
	 * @param startBlock Starting block index for files (set to -1 for directories)
	 */
	public void createEntry(vFile file, int startBlock) {
		file.setStartBlock(startBlock);
		this.files.add(file);
	}

	/**
	 * Retrieves the starting block index of a file in the directory.
	 * @param file vFile instance
	 * @return Starting block index of the file or null if not found
	 */
	public Integer getFileStartBlock(vFile file) {
		for (vFile f: files) {
			if (f.equals(file))
				return f.getStartBlock();
		}
		return null;
	}

	/**
	 * Deletes an entry (file or subdirectory) from the directory.
	 * @param file vFile instance to delete
	 */
	public void deleteEntry(vFile file) {
		this.files.remove(file);
	}

	/**
	 * Gets a vFile instance by its full name (name + "." + type).
	 * @param fullName Full name of the file
	 * @return vFile instance or null if not found
	 */
	public vFile getFileByFullName(String fullName) {
		String[] arr = fullName.split("\\.");
		return getFileByNameAndType(arr[0], arr[1]);
	}

	/**
	 * Gets a vFile instance by its name and type.
	 * @param name Name of the file
	 * @param type Type of the file
	 * @return vFile instance or null if not found
	 */
	public vFile getFileByNameAndType(String name, String type) {
		int res = hash(name, type);
		for (vFile key: files) {
			if (key.hashCode() == res)
				return key;
		}
		return null;
	}

	/**
	 * Gets a subdirectory by its name.
	 * Supports special names "." and ".." for current and parent directories, respectively.
	 * @param name Name of the subdirectory
	 * @return vDirectory instance or null if not found
	 */
	public vDirectory getSubFolderByName(String name) {
		if (Objects.equals(name, "."))
			return this;
		if (Objects.equals(name, ".."))
			return getLocation() == null ? this : getLocation();
		int res = hash(name, null);
		for (vFile key: files) {
			if (key instanceof vDirectory && key.hashCode() == res)
				return (vDirectory) key;
		}
		return null;
	}

	/**
	 * Prints information about all files and subdirectories in the directory.
	 */
	public void printAllFiles() {
		System.out.println("Files in " + this.getName() + " directory:");
		for (vFile file : files) {
			System.out.printf("%s\t\t%s\t\t%s\t\t%d\t\t%s\n", file.getPermissionString(), file.getModificationTime().format(DateTimeFormatter.ISO_DATE_TIME), file.getType() == null ? "<DIR>" : "     ", file.getSize(), file.getFullName());
		}
	}
}
