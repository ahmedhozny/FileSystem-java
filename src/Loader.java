import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Loader {
	// Creating a Scanner object for user input
	static Scanner scanner = new Scanner(System.in);
	// Using a Hashtable to store virtual partitions, mapping partition labels to vPartition instances
	static Hashtable<Character, vPartition> vPartitions = new Hashtable<>();

	/**
	 * Entry point of the virtual file system application
 	 */
	public static void main(String[] __) throws Exception {
		System.out.println("Starting virtual file system (experimental):");
		File directory = new File(".");

		// Check if the specified path is a directory
		if (!directory.isDirectory()) {
			System.err.println("Invalid directory path.");
			System.exit(1);
		}

		// Initialize virtual partitions based on existing .vpar files in the current directory
		for (File file : Objects.requireNonNull(directory.listFiles())) {
			if (file.isFile() && file.getName().endsWith(".vpar")) {
				String part_name = file.getName().replaceFirst("[.][^.]+$", "");
				vPartition partition = new vPartition(part_name);
				vPartitions.put(partition.getPartitionLabel(), partition);
			}
		}

		// Main command loop for user interaction
		while (true) {
			System.out.print("FS > ");

			// Read user input
			String[] args = scanner.nextLine().split(" +");

			// Process user input
			switch (args[0]) {
				case "create":
					// Handle the creation of a new virtual partition
					if (args.length < 3)
						System.out.println("Usage: create <partition label> <partition size in bytes>.");
					else if (args[1].length() != 1)
						System.out.println("Partition label must be a single letter.");
					else if (vPartitions.containsKey(args[1].toUpperCase().charAt(0)))
						System.out.println("Partition already exists.");
					else if (Long.parseLong(args[2]) > 10485760L)
						System.out.println("For experimental reasons, max size of partition is 10 MB");
					else {
						char label = args[1].toUpperCase().charAt(0);
						long size = Long.parseLong(args[2]);
						vPartitions.put(label, new vPartition(label, size));
						System.out.printf("Partition %c created successfully\n", label);
					}
					break;
				case "use":
					// Handle switching to an existing virtual partition
					if (args.length < 2)
						System.out.println("Usage: use <partition label>.");
					else if (args[1].length() != 1)
						System.out.println("Partition label must be a single letter.");
					else if (!vPartitions.containsKey(args[1].toUpperCase().charAt(0)))
						System.out.println("Partition doesn't exists.");
					else
						partitionLooper(vPartitions.get(args[1].toUpperCase().charAt(0)));
					break;
				case "delete":
					// Handle the deletion of an existing virtual partition
					if (args.length < 2)
						System.out.println("Usage: delete <partition label>.");
					else if (args[1].length() != 1)
						System.out.println("Partition label must be a single letter.");
					else if (!vPartitions.containsKey(args[1].toUpperCase().charAt(0)))
						System.out.println("Partition doesn't exists.");
					else {
						char label = args[1].toUpperCase().charAt(0);
						vPartition partition = vPartitions.remove(label);
						partition.forceUnmount(); // Force unmount the partition to ensure proper cleanup
						File file = new File(partition.getUuid().toString() + ".vpar");
						if (file.delete()) // Attempt to delete the .vpar file
							System.out.printf("Partition %c deleted.\n", label);
					}
					break;
				case "exit":
					// Handle the application exit
					System.out.println("Bye!");
					scanner.close();
					System.exit(0);
					return;
				case "":
					// Handle empty input (ignore and continue)
					break;
				default:
					// Handle invalid commands
					System.out.println("Invalid command. Please enter a valid option.");
			}
		}
	}

	/**
	 * 	Main loop for handling user commands within a selected virtual partition.
	 * @param partition: virtual partition instance
	 */
	public static void partitionLooper(vPartition partition) throws IOException {
		char label = partition.getPartitionLabel(); // Get the label of the current partition
		vFolder current_folder = partition.getRoot(); // Set the current folder to the root of the partition
		System.out.printf("Partition %c selected%n", label);

		// Main loop for user interaction within the partition
		super_loop:
		while (true) {
			// Display the current path as prompt
			System.out.printf("%s> ", partition.getPathString(current_folder));
			// Read user input
			String[] args = scanner.nextLine().split(" +");
			try {
				// Process user commands
				switch (args[0]) {
					case "touch":
						// Handle file creation within the current folder
						if (args.length < 2) {
							System.out.println("Usage: touch <file_name>..");
						} else {
							for (int i = 1; i < args.length; i++) {
								if (!args[i].contains(".")) {
									System.out.println("Currently, you must provide provide type to all your files. `<file_name>.<file_type>`");
								} else {
									String[] arr = args[i].split("\\.", 2);
									partition.createFile(current_folder, arr[0], arr[1]);
								}
							}
						}
						break;
					case "rm":
						// Handle file deletion within the current folder
						if (args.length < 2) {
							System.out.println("Usage: rm <file_name>..");
						} else {
							for (int i = 1; i < args.length; i++) {
								if (!args[i].contains(".")) {
									System.out.println("Currently, you must provide provide type to all your files. `<file_name>.<file_type>`");
								} else {
									vFile file = getFile(current_folder, partition, args[i]);
									partition.deleteFile(file.getLocation(), file);
								}
							}
						}
						break;
					case "mkdir":
						// Handle folder creation within the current folder
						if (args.length != 2) {
							System.out.println("Usage: mkdir <folder_name>");
						} else {
							partition.createFolder(current_folder, args[1]);
						}
						break;
					case "rmdir":
						// Handle folder deletion within the current folder
						if (args.length != 2) {
							System.out.println("Usage: rmdir <folder_name>");
						} else {
							partition.deleteFolder(current_folder, args[1]);
						}
						break;
					case "read":
						// Handle reading file content within the current folder
						if (args.length != 2)
							System.out.println("Usage: read <file_name>");
						else if (!args[1].contains("."))
							System.out.println("Currently, you must provide provide type to all your files. `<file_name>.<file_type>`");
						else {
							vFile file = getFile(current_folder, partition, args[1]);
							if (file == null)
								System.out.printf("File %s doesn't exist\n", args[1]);
							else
								System.out.println(new String(partition.getFileData(file.getLocation(), file), StandardCharsets.UTF_8));
						}
						break;
					case "write":
						// Handle writing content to a file within the current folder
						if (args.length != 2)
							System.out.println("Usage: write <file_name>");
						else if (!args[1].contains("."))
							System.out.println("Currently, you must provide provide type to all your files. `<file_name>.<file_type>`");
						else {
							vFile file = getFile(current_folder, partition, args[1]);
							if (file == null)
								System.out.printf("File %s doesn't exist\n", args[1]);
							else {
								System.out.println("Type the content you'd like to enter:");
								String content = scanner.nextLine();
								partition.saveFileData(file.getLocation(), file, content.getBytes(StandardCharsets.UTF_8));
							}
						}
						break;
					case "cd":
						// Handle changing the current folder
						if (args.length != 2) {
							System.out.println("Usage: cd <folder_name>");
						} else {
							if (args[1].equals("~")) {
								current_folder = partition.getRoot();
								break;
							}
							try {
								vFolder folder = current_folder.getSubFolderByName(args[1]);
								folder = folder == null && args[1].matches("^/.*$")? partition.getFolderByPath(args[1]) : folder;
								if (folder == null)
									System.out.println("Folder doesn't exist");
								else
									current_folder = folder;
							} catch (IllegalArgumentException e) {
								System.out.println(e.getMessage());
							}
						}
						break;
					case "mv":
						// Handle moving a file within the partition
						if (args.length != 3) {
							System.out.println("Usage: mv <source_file> <destination_file>");
						} else {
							String sourceFilePath = args[1];
							String destinationFilePath = args[2];

							vFile sourceFile = getFile(current_folder, partition, sourceFilePath);
							if (sourceFile == null) {
								System.out.printf("File %s doesn't exist\n", sourceFilePath);
								break;
							}
							vFolder destDirectory = getFolder(current_folder, partition, destinationFilePath);
							if (destDirectory == null) {
								System.out.printf("Folder %s doesn't exist\n", destinationFilePath);
								break;
							}
							String destFile = destinationFilePath.substring(destinationFilePath.lastIndexOf("/") + 1);
							partition.moveFile(sourceFile.getLocation(), sourceFile, destDirectory, destFile);
							System.out.printf("File %s moved to %s\n", sourceFilePath, destinationFilePath);
						}
						break;
					case "cp":
						// Handle copying a file within the partition
						if (args.length != 3) {
							System.out.println("Usage: cp <source_file> <destination_file>");
						} else {
							String sourceFilePath = args[1];
							String destinationFilePath = args[2];

							vFile sourceFile = getFile(current_folder, partition, sourceFilePath);
							if (sourceFile == null) {
								System.out.printf("File %s doesn't exist\n", sourceFilePath);
								break;
							}
							vFolder destDirectory = getFolder(current_folder, partition, destinationFilePath);
							if (destDirectory == null) {
								System.out.printf("Folder %s doesn't exist\n", destinationFilePath);
								break;
							}
							String destFile = destinationFilePath.substring(destinationFilePath.lastIndexOf("/") + 1);
							partition.copyFile(sourceFile.getLocation(), sourceFile, destDirectory, destFile);
							System.out.printf("File %s copied to %s\n", sourceFilePath, destinationFilePath);
						}
						break;
					case "dir":
						// Display the current folder details
						System.out.println(current_folder);
						break;
					case "ls":
						// List all files in the current folder
						current_folder.printAllFiles();
						break;
					case "show":
						// Display detailed information about a specific file
						if (args.length != 2)
							System.out.println("Usage: show <file_name>");
						else if (!args[1].contains("."))
							System.out.println("Currently, you must provide provide type to all your files. `<file_name>.<file_type>`");
						else {
							vFile file = getFile(current_folder, partition, args[1]);
							if (file == null)
								System.out.printf("File %s doesn't exist\n", args[1]);
							else
								System.out.println(file);
						}
						break;
					case "chmod":
						// Change file permissions within the current folder
						if (args.length != 3)
							System.out.println("Usage: chmod <OPTION>... MODE[,MODE] <file_name>");
						else if (!args[2].contains("."))
							System.out.println("Currently, you must provide provide type to all your files. `<file_name>.<file_type>`");
						else {
							String permissions = args[1];
							String fileName = args[2];
							vFile file = getFile(current_folder, partition, fileName);
							if (file == null)
								System.out.printf("File %s doesn't exist\n", fileName);
							else {
								List<Runnable> permissionCommands = new ArrayList<>();
								boolean addPermission = permissions.charAt(0) == '+';
								permissions = permissions.substring(1);
								for (char permission : permissions.toCharArray()) {
									switch (permission) {
										case 'r' -> permissionCommands.add(() -> file.setReadPermission(addPermission));
										case 'w' ->
												permissionCommands.add(() -> file.setWritePermission(addPermission));
										case 'x' ->
												permissionCommands.add(() -> file.setExecutePermission(addPermission));
										default -> {
											System.out.println("Invalid permission character: " + permission);
											continue super_loop;
										}
									}
								}
								permissionCommands.forEach(Runnable::run);
								System.out.printf("Permissions for %s updated\n", fileName);
							}
						}
						break;
					// Search for files containing a specific value within the current folder
					case "search":
						if (args.length != 2)
							System.out.println("Usage: search <value>");
						else
							current_folder.printSomeFiles(args[1]);
						break;
					case "exit":
						// Exit to the File System
						System.out.println("Exiting to File System");
						return;
					case "info":
						// Display information about the current partition
						System.out.println("________________________");
						System.out.print(partition);
						System.out.println("________________________");
						break;
					case "":
						// Handle empty input (ignore and continue)
						break;
					default:
						// Handle invalid commands
						System.out.println("Invalid command. Please enter a valid option.");
				}
			} catch (RuntimeException e) {
				// Handle runtime exceptions and display an error message
				System.out.println(e.getMessage());
			}
			// Save the state of the partition after each command
			partition.save();
		}
	}

	/**
	 * Retrieve a vFile object based on the provided path within the current folder and partition
	 * @param currentFolder: The current working directory within the partition
	 * @param partition: The virtual partition containing the file
	 * @param path: The path of the file to retrieve
	 * @return vFile object corresponding to the specified path, or null if not found
	 */
	private static vFile getFile(vFolder currentFolder, vPartition partition, String path) {
		// Initialize a variable to hold the resulting vFile object
		vFile file = null;

		// Check if the path is an absolute path
		if (!path.matches("^/.*$")) {
			// If the path is not absolute, directly retrieve the file from the current folder
			file = currentFolder.getFileByFullName(path);
		} else {
			// If the path is absolute, extract the directory and file name
			vFolder directory = getFolder(currentFolder, partition, path);
			// Check if the directory exists
			if (directory != null) {
				// Retrieve the file from the specified directory
				String file_name = path.substring(path.lastIndexOf("/") + 1);
				file = directory.getFileByFullName(file_name);
			}
		}
		return file;
	}

	/**
	 * Retrieve a vDirectory object based on the provided path within the current folder and partition
	 * @param currentFolder: The current working directory within the partition
	 * @param partition: The virtual partition containing the directory
	 * @param path: The path of the directory to retrieve
	 * @return vDirectory object corresponding to the specified path, or the current folder if the path is not absolute
	 */
	private static vFolder getFolder(vFolder currentFolder, vPartition partition, String path) {
		// Check if the path is not absolute
		if (!path.matches("^/.*$")) {
			// If the path is not absolute, return the current folder
			return currentFolder;
		} else {
			// Retrieve the directory from the specified path in the partition
			String dir_name = path.substring(0, path.lastIndexOf("/"));
			return partition.getFolderByPath(dir_name);
		}
	}
}