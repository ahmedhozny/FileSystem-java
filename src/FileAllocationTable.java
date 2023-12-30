import java.io.Serializable;
import java.util.*;

public class FileAllocationTable implements Serializable {
	private final Integer[] blocks;
	private final Map<String, Integer> fileEntries;

	public FileAllocationTable(int numBlocks) {
		blocks = new Integer[numBlocks];
		fileEntries = new HashMap<>();
	}

	public int allocateBlock() {
		for (int i = 0; i < blocks.length; i++) {
			if (blocks[i] != null) {
				blocks[i] = -1;
				return i;
			}
		}
		return -1;
	}

	public void deallocateBlock(int blockIndex) {
		if (blockIndex >= 0 && blockIndex < blocks.length)
			blocks[blockIndex] = null;
	}

	public void setNextBlock(int blockIndex, int nextBlock) {
		if (blockIndex >= 0 && blockIndex < blocks.length) {
			blocks[blockIndex] = nextBlock;
		}
	}

	public void createFile(String fileName, int startBlock) {
		fileEntries.put(fileName, startBlock);
	}

	public int getFileStartBlock(String fileName) {
		return fileEntries.getOrDefault(fileName, -1);
	}

	public void deleteFile(String fileName) {
		fileEntries.remove(fileName);
	}
}

