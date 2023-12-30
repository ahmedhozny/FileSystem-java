import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;

public class Loader {
	static Scanner scanner = new Scanner(System.in);
	static Hashtable<Character, vPartition> vPartitions = new Hashtable<>();

	public static void main(String[] __) throws FileNotFoundException, FileAlreadyExistsException {
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
				case "exit":
					System.out.println("Goodbye!");
					scanner.close();
					System.exit(0);
					break;
				default:
					System.out.println("Invalid command. Please enter a valid option.");
			}
		}
	}

	public static void partitionLooper(vPartition vPartition) {
		char label = vPartition.getPartitionLabel();
		System.out.printf("Partition %c selected%n", label);
		while (true) {
			System.out.printf("%c:\\> ", label);
			String[] args = scanner.nextLine().split(" +");
			switch (args[0]) {
				case "exit":
					System.out.println("Exiting to File System");
					return;

				default:
					System.out.println("Invalid command. Please enter a valid option.");
			}
		}
	}
}