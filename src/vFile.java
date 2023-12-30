import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Objects;
import java.nio.file.attribute.FileTime;

public class vFile implements Serializable {
    private String name;
    private vDirectory location;
    private String type;
    private long size;
    private int numOfBlocks;
    private byte protection;
    private LocalDateTime creationTime;
    private LocalDateTime modificationTime;
    private LocalDateTime accessTime;

    // Constructor
    public vFile(String name, vDirectory location, String type) {
        this.name = name;
        this.location = location;
        this.type = type;
        this.size = 0;
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
        if (type != null && !type.isEmpty()) {
            this.type = type;
        } else {
            throw new IllegalArgumentException("Type must not be null or empty");
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
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        vFile vFile = (vFile) obj;
        return name.equals(vFile.name);
    }

    @Override
    public int hashCode() {
        String uniqueKey = name + location + type;
        return uniqueKey.hashCode();
    }

    public static void main(String[] args) {
        HashMap<Integer, vFile> fileHashTable = new HashMap<>();

        vDirectory directory = new vDirectory("ExampleDirectory");
        vFile file1 = new vFile("File1", directory, "txt");
        vFile file2 = new vFile("File2", directory, "pdf");

        fileHashTable.put(file1.hashCode(), file1);
        fileHashTable.put(file2.hashCode(), file2);

        int fileIdToRetrieve = file1.hashCode();
        vFile retrievedFile = fileHashTable.get(fileIdToRetrieve);

    }

}
