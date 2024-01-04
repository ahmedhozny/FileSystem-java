import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a virtual folder in the file system.
 */
public class vFolder extends vFile implements Serializable {
	private final HashSet<vFile> files;
	/**
	 * Constructor for creating a new vFolder.
	 * @param name Name of the folder
	 * @param location Parent folder (null for the root folder)
	 */
	public vFolder(String name, vFolder location) {
		super(name, null, location);
		if (Objects.equals(name, ".") || Objects.equals(name, ".."))
			System.out.println();
		this.files = new HashSet<>();
	}

	/**
	 * Constructor for creating a new vFolder by copying an existing one.
	 * @param folder Existing vFolder to copy
	 */
	public vFolder(vFolder folder) {
		super(folder.getName(), null, folder.getLocation());
		this.files = folder.files;
	}

	/**
	 * Creates a new entry (file or sub-folder) in the folder.
	 * @param file vFile instance representing the new entry
	 * @param startBlock Starting block index for files (set to -1 for directories)
	 */
	public void createEntry(vFile file, int startBlock) {
		file.setStartBlock(startBlock);
		this.files.add(file);
	}

	/**
	 * Retrieves the starting block index of a file in the folder.
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
	 * Deletes an entry (file or sub-folder) from the folder.
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
	 * Gets a sub-folder by its name.
	 * Supports special names "." and ".." for current and parent directories, respectively.
	 * @param name Name of the sub-folder
	 * @return vFolder instance or null if not found
	 */
	public vFolder getSubFolderByName(String name) {
		if (Objects.equals(name, "."))
			return this;
		if (Objects.equals(name, ".."))
			return getLocation() == null ? this : getLocation();
		int res = hash(name, null);
		for (vFile key: files) {
			if (key instanceof vFolder && key.hashCode() == res)
				return (vFolder) key;
		}
		return null;
	}

	public String toString() {
		long size = 0;
		for (vFile file: files)
			size += file.getSize();
		return "Folder: " + getName() + "\n" +
				String.format("Size: %d bytes\n", size) +
				String.format("Permissions: %s\n", getPermissionString()) +
				String.format("Created: %s\n", getCreationTime()) +
				String.format("Last Modified: %s\n", getModificationTime()) +
				String.format("Last Accessed: %s\n", getAccessTime());
	}

	/**
	 * Prints information about all files and subdirectories in the folder.
	 */
	public void printAllFiles() {
		System.out.println("Files in " + this.getName() + " folder:");
		for (vFile file : files) {
			if (file instanceof vFolder)
				System.out.printf("%s\t\t%s\t\t%s\t\t%s\t\t%s\n", file.getPermissionString(), file.getModificationTime(), "<DIR>", "", file.getFullName());
			else
				System.out.printf("%s\t\t%s\t\t%s\t\t%d\t\t%s\n", file.getPermissionString(), file.getModificationTime(), "     ", file.getSize(), file.getFullName());
		}
	}

	/**
	 * Prints information about files matching the search criteria.
	 *
	 * @param search The string to search for in file names.
	 */
	public void printSomeFiles(String search) {
		// List to store files matching the search criteria
		List<vFile> found = new LinkedList<>();

		// Iterate through files to find matches
		for (vFile file : files) {
			if (file.getFullName().contains(search))
				found.add(file);
		}

		// Check if any files match the search criteria
		if (found.isEmpty())
			System.out.println("No files match the search criteria.");
		else {
			// Print information about matching files
			for (vFile file : found) {
				if (file instanceof vFolder)
					System.out.printf("%s\t\t%s\t\t%s\t\t%s\t\t%s\n", file.getPermissionString(), file.getModificationTime(), "<DIR>", "", file.getFullName());
				else
					System.out.printf("%s\t\t%s\t\t%s\t\t%d\t\t%s\n", file.getPermissionString(), file.getModificationTime(), "     ", file.getSize(), file.getFullName());
			}
		}
	}

	public HashSet<vFile> getFiles() {
		return files;
	}
}
