import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Loader {
	static Scanner scanner = new Scanner(System.in);
	static Hashtable<Character, vPartition> vPartitions = new Hashtable<>();

	public static void main(String[] __) throws Exception {
		System.out.println("Starting virtual file system (experimental):");
		File directory = new File(".");

		// Check if the specified path is a directory
		if (!directory.isDirectory()) {
			System.err.println("Invalid directory path.");
			System.exit(1);
		}

		for (File file : Objects.requireNonNull(directory.listFiles())) {
			if (file.isFile() && file.getName().endsWith(".vpar")) {
				String part_name = file.getName().replaceFirst("[.][^.]+$", "");
				vPartition partition = new vPartition(part_name);
				vPartitions.put(partition.getPartitionLabel(), partition);
			}
		}

		while (true) {
			System.out.print("FS > ");

			// Read user input
			String[] args = scanner.nextLine().split(" +");

			// Process user input
			switch (args[0]) {
				case "create":
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
						vPartitions.put(args[1].charAt(0), new vPartition(label, size));
					}
					break;
				case "use":
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
					if (args.length < 2)
						System.out.println("Usage: use <partition label>.");
					else if (args[1].length() != 1)
						System.out.println("Partition label must be a single letter.");
					else if (!vPartitions.containsKey(args[1].toUpperCase().charAt(0)))
						System.out.println("Partition doesn't exists.");
					else
					{
						char label = args[1].toUpperCase().charAt(0);
						File file = new File(vPartitions.remove(label).getUuid().toString() + ".vpar");
						if (file.delete())
							System.out.printf("Partition %c removed.\n", label);
					}
					break;
				case "exit":
					System.out.println("Bye!");
					scanner.close();
					System.exit(0);
					return;
				default:
					System.out.println("Invalid command. Please enter a valid option.");
			}
		}
	}

	public static void partitionLooper(vPartition partition) throws IOException {
		char label = partition.getPartitionLabel();
		vDirectory current_folder = partition.getRoot();
		System.out.printf("Partition %c selected%n", label);
		while (true) {
			System.out.printf("%s> ", partition.getPathString(current_folder));
			String[] args = scanner.nextLine().split(" +");
			switch (args[0]) {
				case "touch":
					if (args.length < 2) {
						System.out.println("Usage: touch <file_name>..");
					} else {
						for (int i = 1; i < args.length; i++) {
							String[] arr = args[i].split("\\.", 2);
							partition.createFile(current_folder, arr[0], arr[1]);
						}
					}
					break;
				case "rm":
					if (args.length < 2) {
						System.out.println("Usage: rm <file_name>..");
					} else {
						for (int i = 1; i < args.length; i++) {
							String[] arr = args[i].split("\\.", 2);
							partition.deleteFile(current_folder, arr[0], arr[1]);
						}
					}
					break;
				case "mkdir":
					if (args.length != 2) {
						System.out.println("Usage: mkdir <folder_name>");
					} else {
						partition.createFolder(current_folder, args[1]);
					}
					break;
				case "rmdir":
					if (args.length != 2) {
						System.out.println("Usage: rmdir <folder_name>");
					} else {
						partition.deleteFolder(current_folder, args[1]);
					}
					break;
				case "read":
					if (args.length != 2) {
						System.out.println("Usage: read <file_name>");
					} else {
						vFile file = current_folder.getFileByFullName(args[1]);
						if (file == null)
							System.out.printf("File %s doesn't exist\n", args[1]);
						else
							System.out.println(new String(partition.getFileData(current_folder, file), StandardCharsets.UTF_8));
					}
					break;
				case "write":
					if (args.length != 2) {
						System.out.println("Usage: write <file_name>");
					} else {
						vFile file = current_folder.getFileByFullName(args[1]);
						if (file == null)
							System.out.printf("File %s doesn't exist\n", args[1]);
						else {
							System.out.println("Type the content you'd like to enter:");
							String content = scanner.nextLine();
							partition.saveFileData(current_folder, file, content.getBytes(StandardCharsets.UTF_8));
						}
					}
					break;
				case "cd":
					if (args.length != 2) {
						System.out.println("Usage: cd <folder_name>");
					} else {
						try {
							vDirectory folder = current_folder.getSubFolderByName(args[1]);
							folder = folder == null ? partition.getDirectoryByPath(args[1]) : folder;
							if (folder == null) {
								System.out.println("Folder doesn't exist");
							}
							else
								current_folder = folder;
						} catch (IllegalArgumentException e) {
							System.out.println(e.getMessage());
						}
					}
					break;
				case "mv":
					if (args.length != 3) {
						System.out.println("Usage: mv <source_file> <destination_file>");
					} else {
						String sourceFilePath = args[1];
						String destinationFilePath = args[2];

						try {
							vFile sourceFile = current_folder.getFileByFullName(sourceFilePath);
							if (sourceFile == null)
								System.out.printf("File %s doesn't exist\n", sourceFilePath);
							else {
								String destFile = destinationFilePath;
								vDirectory destinationDirectory = current_folder;
								if (destinationFilePath.contains("/")) {
									destFile = destinationFilePath.substring(destinationFilePath.lastIndexOf("/") + 1);
									String destDir = destinationFilePath.substring(0, destinationFilePath.lastIndexOf("/"));
									destinationDirectory = partition.getDirectoryByPath(destDir);
								}

								if (destinationDirectory == null) {
									System.out.println("Destination folder doesn't exist");
								}

								partition.moveFile(current_folder, sourceFile, destinationDirectory, destFile);
								System.out.printf("File %s moved to %s\n", sourceFilePath, destinationFilePath);

							}
						} catch (IllegalArgumentException e) {
							System.out.println(e.getMessage());
						}
					}
					break;
				case "cp":
					if (args.length != 3) {
						System.out.println("Usage: cp <source_file> <destination_file>");
					} else {
						String sourceFilePath = args[1];
						String destinationFilePath = args[2];

						try {
							vFile sourceFile = current_folder.getFileByFullName(sourceFilePath);
							if (sourceFile == null)
								System.out.printf("File %s doesn't exist\n", sourceFilePath);
							else {
								String destFile = destinationFilePath;
								vDirectory destinationDirectory = current_folder;
								if (destinationFilePath.contains("/")) {
									destFile = destinationFilePath.substring(destinationFilePath.lastIndexOf("/") + 1);
									String destDir = destinationFilePath.substring(0, destinationFilePath.lastIndexOf("/"));
									destinationDirectory = partition.getDirectoryByPath(destDir);
								}

								if (destinationDirectory == null) {
									System.out.println("Destination folder doesn't exist");
								}

								partition.copyFile(current_folder, sourceFile, destinationDirectory, destFile);
								System.out.printf("File %s copied to %s\n", sourceFilePath, destinationFilePath);

							}
						} catch (IllegalArgumentException e) {
							System.out.println(e.getMessage());
						}
					}
					break;
				case "dir":
					current_folder.printAllFiles();
					break;
				case "show":
					if (args.length != 2) {
						System.out.println("Usage: show <file_name>");
					} else {
						vFile file = current_folder.getFileByFullName(args[1]);
						if (file == null)
							System.out.printf("File %s doesn't exist\n", args[1]);
						else
							System.out.println(file);
						break;
					}

				case "exit":
					System.out.println("Exiting to File System");
					return;
				case "info":
					System.out.println("________________________");
					System.out.print(partition);
					System.out.println("________________________");
					break;
				default:
					System.out.println("Invalid command. Please enter a valid option.");
			}
			partition.save();
		}
	}
}