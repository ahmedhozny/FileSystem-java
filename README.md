# Virtual File System

This project implements a virtual file system in Java. It includes classes to manage virtual files and folders, as well as a File Allocation Table (FAT) to keep track of block allocations. This can be used to simulate file storage, manipulation, and organization within a virtual environment.

## Classes Overview

### `vFile`

Represents a virtual file in the file system. Key functionalities include:

- Setting and getting file attributes such as name, type, size, and permissions.
- Handling file timestamps for creation, modification, and access times.
- Managing read, write, and execute permissions.
- Utility methods to get full file name, permission string, and file details.

### `vFolder`

Extends `vFile` to represent a virtual folder, with additional functionalities:

- Creating and managing entries (files and sub-folders) within the folder.
- Retrieving files by name, type, or full name.
- Handling special folder names like `.` (current folder) and `..` (parent folder).
- Printing details of all files and folders contained within the folder.
- Searching and printing files based on search criteria.

### `FileAllocationTable`

Manages block allocation for a virtual partition. Key functionalities include:

- Allocating and deallocating blocks.
- Tracking next blocks in the file allocation chain.
- Utility methods to get and set the next block for a given block index.

## Usage

### Example

Here’s an example of how you might use these classes to create and manage a virtual file system:

```java
public class Main {
    public static void main(String[] args) {
        // Create the root folder
        vFolder root = new vFolder("root", null);

        // Create a sub-folder
        vFolder documents = new vFolder("documents", root);
        root.createEntry(documents, -1);

        // Create a file
        vFile file1 = new vFile("file1", "txt", documents);
        documents.createEntry(file1, -1);

        // Print all files in the root folder
        root.printAllFiles();

        // Print details of the 'documents' folder
        System.out.println(documents);
    }
}

```

### Running the Project

To compile and run the project, follow these steps:

1. **Clone the Repository:**

   ```bash
   git clone https://github.com/ahmedhozny/virtual-file-system.git
   cd virtual-file-system
   ```
   
2. **Compile the Java Files:**
   ```bash
   javac Main.java vFile.java vFolder.java FileAllocationTable.java
   ```
   
3. **Run the Main Class:**
   ```bash
   java Main
   ```

## Project Structure

```text
FileSystem-java/
├── Main.java
├── vFile.java
├── vFolder.java
└── FileAllocationTable.java
```

## Contributing
Contributions are welcome! If you have any improvements or new features you would like to add, please fork the repository, make your changes, and submit a pull request.
