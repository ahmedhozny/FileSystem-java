import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Objects;

public class vDirectory extends vFile implements Serializable {
	private final HashSet<vFile> files;
	public vDirectory(String name, vDirectory location) {
		super(name, null, location);
		this.files = new HashSet<>();
	}

	public vDirectory(vDirectory directory) {
		super(directory.getName(), null, directory.getLocation());
		this.files = directory.files;
	}

	public void createEntry(vFile file, int startBlock) {
		file.setStartBlock(startBlock);
		this.files.add(file);
	}

	public Integer getFileStartBlock(vFile file) {
		for (vFile f: files) {
			if (f.equals(file))
				return f.getStartBlock();
		}
		return null;
	}

	public void deleteEntry(vFile file) {
		this.files.remove(file);
	}

	public vFile getFileByFullName(String fullName) {
		String[] arr = fullName.split("\\.");
		return getFileByNameAndType(arr[0], arr[1]);
	}

	public vFile getFileByNameAndType(String name, String type) {
		int res = hash(name, type);
		for (vFile key: files) {
			if (key.hashCode() == res)
				return key;
		}
		return null;
	}

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

	public void printAllFiles() {
		System.out.println("Files in " + this.getName() + " directory:");
		for (vFile file : files) {
			System.out.printf("%s\t\t%s\t\t%d\t\t%s\n", file.getModificationTime().format(DateTimeFormatter.ISO_DATE_TIME), file.getType() == null ? "<DIR>" : "     ", file.getSize(), file.getFullName());
		}
	}
}
