import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Objects;

public class vFile implements Serializable {
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
        this.protection = 0;
        this.creationTime = LocalDateTime.now();
        this.modificationTime = this.creationTime;
        this.accessTime = this.creationTime;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name.length() <= 9) {
            this.name = name;
            this.setModificationTime(LocalDateTime.now());
        } else {
            throw new IllegalArgumentException("Name must be 9 characters or less");
        }
    }

    public vDirectory getLocation() {
        return location;
    }

    public void setLocation(vDirectory location) {
        this.location = location;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        if (this instanceof vDirectory)
            this.type = type;
        else if (type.length() <= 3) {
            this.type = type;
            this.setModificationTime(LocalDateTime.now());
        } else {
            throw new IllegalArgumentException("Type must be 3 characters or less");
        }
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        if (size >= 0) {
            this.size = size;
        } else {
            throw new IllegalArgumentException("Size must be non-negative");
        }
    }

    public int getNumOfBlocks() {
        return numOfBlocks;
    }

    public void setNumOfBlocks(int numOfBlocks) {
        if (numOfBlocks >= 0) {
            this.numOfBlocks = numOfBlocks;
        } else {
            throw new IllegalArgumentException("Number of blocks must be non-negative");
        }
    }

    public byte getProtection() {
        return protection;
    }

    public void setProtection(byte protection) {
        if (protection >= 0 && protection <= 127) {
            this.protection = protection;
        } else {
            throw new IllegalArgumentException("Invalid protection value");
        }
    }

    public LocalDateTime getCreationTime() {
        return creationTime;
    }

    public LocalDateTime getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(LocalDateTime modificationTime) {
        setAccessTime(modificationTime);
        this.modificationTime = modificationTime;
    }

    public LocalDateTime getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(LocalDateTime accessTime) {
        this.accessTime = accessTime;
    }

    // Override toString, equals, and hashCode methods...
    @Override
    public String toString() {
        return "vFile{" +
                "name='" + name + '\'' +
                ", identifier=" + hashCode() +
                ", location=" + location +
                ", type='" + type + '\'' +
                ", size=" + size +
                ", numOfBlocks=" + numOfBlocks +
                ", protection=" + protection +
                ", creationTime=" + creationTime +
                ", modificationTime=" + modificationTime +
                ", accessTime=" + accessTime +
                '}';
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

    public int getStartBlock() {
        return startBlock;
    }

    public void setStartBlock(int startBlock) {
        this.startBlock = startBlock;
    }

    public String getFullName() {
        return name + (type == null ? "" : "." + type);
    }
}
