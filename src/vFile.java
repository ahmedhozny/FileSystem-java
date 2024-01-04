import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents a virtual file in the file system.
 */
public class vFile implements Serializable {
	// Date and time formatter for consistent formatting
	public static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
	// Constants for permission levels
	public static final byte READ_PERMISSION = 4;       // 100 in binary
	public static final byte WRITE_PERMISSION = 2;      // 010 in binary
	public static final byte EXECUTE_PERMISSION = 1;    // 001 in binary
	private String name;
	private vDirectory location;
	private String type;
	private long size;
	private int startBlock;
	private int numOfBlocks;
	private byte protection;
	private final LocalDateTime creationTime;
	private LocalDateTime modificationTime;
	private LocalDateTime accessTime;

	// Static method to calculate a hash based on name and type
	public static int hash(String name, String type) {
		String uniqueKey = name  + type;
		return uniqueKey.hashCode();
	}

	// Constructor
	public vFile(String name, String type, vDirectory location) {
		setName(name);
		setType(type);
		setLocation(location);
		this.size = 0;
		this.startBlock = -1;
		this.numOfBlocks = 0;
		this.protection = READ_PERMISSION + WRITE_PERMISSION;
		this.creationTime = LocalDateTime.now();
		this.modificationTime = this.creationTime;
		this.accessTime = this.creationTime;
	}

	// Setters
	public void setName(String name) {
		if (name == null || name.isEmpty() || name.length() > 9)
			throw new IllegalArgumentException("Name must be 9 characters or less");
		this.name = name;
		this.setModificationTime(LocalDateTime.now());
	}

	public void setLocation(vDirectory location) {
		this.location = location;
	}

	public void setType(String type) {
		if (this instanceof vDirectory || type.length() <= 3) {
			this.type = type;
			this.setModificationTime(LocalDateTime.now());
		} else {
			throw new IllegalArgumentException("Type must be 3 characters or less");
		}
	}

	public void setSize(long size) {
		if (size < 0)
			throw new IllegalArgumentException("Size must be non-negative");
		this.size = size;
	}

	public void setNumOfBlocks(int numOfBlocks) {
		if (numOfBlocks >= 0) {
			this.numOfBlocks = numOfBlocks;
		} else {
			throw new IllegalArgumentException("Number of blocks must be non-negative");
		}
	}

	/**
	 * Set read permission for the file.
	 * @param read true to set read permission, false to unset.
	 */
	public void setReadPermission(boolean read) {
		if (read) {
			protection |= READ_PERMISSION;
		} else {
			protection &= ~READ_PERMISSION;
		}
	}

	/**
	 * Set write permission for the file.
	 * @param write true to set write permission, false to unset.
	 */
	public void setWritePermission(boolean write) {
		if (write) {
			protection |= WRITE_PERMISSION;
		} else {
			protection &= ~WRITE_PERMISSION;
		}
	}

	/**
	 * Set execute permission for the file.
	 * @param execute true to set execute permission, false to unset.
	 */
	public void setExecutePermission(boolean execute) {
		if (execute) {
			protection |= EXECUTE_PERMISSION;
		} else {
			protection &= ~EXECUTE_PERMISSION;
		}
	}

	public void setModificationTime(LocalDateTime modificationTime) {
		setAccessTime(modificationTime);
		this.modificationTime = modificationTime;
	}

	public void setAccessTime(LocalDateTime accessTime) {
		this.accessTime = accessTime;
	}

	public void setStartBlock(int startBlock) {
		this.startBlock = startBlock;
	}

	// Getters
	public String getName() {
		return name;
	}

	public vDirectory getLocation() {
		return location;
	}

	public String getType() {
		return type;
	}

	public long getSize() {
		return size;
	}

	public int getNumOfBlocks() {
		return numOfBlocks;
	}

	public boolean hasReadPermission() {
		return (protection & READ_PERMISSION) == READ_PERMISSION;
	}
	public boolean hasWritePermission() {
		return (protection & WRITE_PERMISSION) == WRITE_PERMISSION;
	}

	public boolean hasExecutePermission() {
		return (protection & EXECUTE_PERMISSION) == EXECUTE_PERMISSION;
	}

	/**
	 * Gets a string representation of the file's permissions.
	 *
	 * @return A string representing the file's permissions in the format "rwx".
	 */
	public String getPermissionString() {
		StringBuilder permissionString = new StringBuilder();
		// Iterate over permission bits (r, w, x) from left to right
		for (int i = 2; i >= 0; i--) {
			// Check if the corresponding permission bit is set
			char permission = ((protection >> i) & 1) == 1 ? "rwx".charAt(2 - i) : '-';
			// Append the permission character to the StringBuilder
			permissionString.append(permission);
		}

		return permissionString.toString();
	}

	public String getCreationTime() {
		return dateFormat.format(creationTime);
	}

	public String getModificationTime() {
		return dateFormat.format(modificationTime);
	}

	public String getAccessTime() {
		return dateFormat.format(accessTime);
	}

	public int getStartBlock() {
		return startBlock;
	}

	public String getFullName() {
		return name + (type == null ? "" : "." + type);
	}

	// Override toString, equals, and hashCode methods...
	@Override
	public String toString() {
		return String.format("""
						File: %s.%s
						Size: %d bytes
						Permissions: %s
						Location: %s
						Created: %s
						Last Accessed: %s
						Last Modified: %s""",
				name, type, size, getPermissionString(), location.getName(), dateFormat.format(creationTime), dateFormat.format(accessTime), dateFormat.format(modificationTime));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass())
			return false;
		vFile file = (vFile) obj;
		if (this.hashCode() == obj.hashCode())
			return true;
		return Objects.equals(this.name, file.name) && Objects.equals(this.type, file.type) && Objects.equals(this.location, file.location);
	}

	@Override
	public int hashCode() {
		return hash(name, type);
	}
}
