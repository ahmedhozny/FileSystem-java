import java.io.Serializable;
import java.util.*;

public class FileAllocationTable implements Serializable {
	private final Integer[] blocks;
	private final Map<String, Integer> fileEntries;

	/**
	 * Creates a new FileAllocationTable
	 * Should be called only if new partition is created.
	 * @param numBlocks: number of blocks on partition that are defined for data saving.
	 */
	public FileAllocationTable(int numBlocks) {
		blocks = new Integer[numBlocks];
		fileEntries = new HashMap<>();
	}

	/**
	 * Allocates new block.
	 * @return : returns index of block found.
	 */
	public int allocateBlock() {
		for (int i = 0; i < blocks.length; i++) {
			if (blocks[i] != null) {
				blocks[i] = -1;
				return i;
			}
		}
		return -1;
	}

	/**
	 * Deallocates a block.
	 * @param blockIndex: index of block to deallocate.
	 */
	public void deallocateBlock(int blockIndex) {
		if (blockIndex >= 0 && blockIndex < blocks.length)
			blocks[blockIndex] = null;
	}

	/**
	 * Checks if block is allocated.
	 * @param blockNumber: index of block.
	 * @return : true if block is allocated, false if not.
	 */
	public boolean isBlockAllocated(int blockNumber) {
		return blocks[blockNumber] != null;
	}

	/**
	 * Gets next block from current block index.
	 * @param blockIndex: index of block that needs to point to the next block.
	 */
	public Integer getNextBlock(int blockIndex) {
		if (blockIndex >= 0 && blockIndex < blocks.length) {
			return blocks[blockIndex];
		}

		return null;
	}

	/**
	 * Sets next block of current block index to point to next block index.
	 * @param blockIndex: index of block that needs to point to the next block.
	 * @param nextBlock: index of block to be pointed to.
	 */
	public void setNextBlock(int blockIndex, int nextBlock) {
		if (blockIndex >= 0 && blockIndex < blocks.length) {
			blocks[blockIndex] = nextBlock;
		}
	}

	public void createEntry(String fileName, Integer startBlock) {
		fileEntries.put(fileName, startBlock);
	}

	public Integer getFileStartBlock(String fileName) {
		return fileEntries.getOrDefault(fileName, null);
	}

	public int deleteEntry(String fileName) {
		int counter = 0;
		Integer idx = fileEntries.getOrDefault(fileName, null);
		while (idx != null && idx != -1) {
			deallocateBlock(idx);
			idx = blocks[idx];
			counter++;
		}
		fileEntries.remove(fileName);
		return counter;
	}
}
