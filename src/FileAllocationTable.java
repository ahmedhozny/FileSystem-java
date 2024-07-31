import java.io.Serializable;

/**
 * Represents the File Allocation Table (FAT) for tracking block allocation in a virtual partition.
 * The FAT is a data structure that keeps track of which blocks are allocated or free.
 */
public class FileAllocationTable implements Serializable {
	public final Integer[] blocks;

	/**
	 * Creates a new FileAllocationTable for a specified number of blocks.
	 * Should be called only if a new partition is created.
	 *
	 * @param numBlocks The number of blocks on the partition defined for data saving.
	 */
	public FileAllocationTable(int numBlocks) {
		blocks = new Integer[numBlocks];
	}

	/**
	 * Copy constructor to create a new FileAllocationTable based on an existing one.
	 *
	 * @param fat The existing FileAllocationTable to copy.
	 */
	public FileAllocationTable(FileAllocationTable fat) {
		this.blocks = fat.blocks;
	}

	/**
	 * Allocates a new block in the FAT.
	 *
	 * @return The index of the allocated block, or -1 if no block is available.
	 * @throws RuntimeException: if no deallocated block is found.
	 */
	public int allocateBlock() {
		for (int i = 0; i < blocks.length; i++) {
			if (blocks[i] == null) {
				blocks[i] = -1;
				return i;
			}
		}
		throw new RuntimeException("Couldn't allocate more space. Partition is full.");
	}

	/**
	 * Deallocates a block in the FAT.
	 *
	 * @param blockIndex The index of the block to deallocate.
	 */
	public void deallocateBlock(int blockIndex) {
		if (blockIndex >= 0 && blockIndex < blocks.length)
			blocks[blockIndex] = null;
	}

	/**
	 * Gets the index of the next block from the current block index.
	 *
	 * @param blockIndex The index of the current block.
	 * @return The index of the next block, or null if not available.
	 */
	public Integer getNextBlock(int blockIndex) {
		if (blockIndex >= 0 && blockIndex < blocks.length) {
			return blocks[blockIndex];
		}

		return null;
	}

	/**
	 * Sets the next block of the current block index to point to a specified next block index.
	 *
	 * @param blockIndex The index of the current block.
	 * @param nextBlock  The index of the block to be pointed to.
	 */
	public void setNextBlock(int blockIndex, int nextBlock) {
		if (blockIndex >= 0 && blockIndex < blocks.length) {
			blocks[blockIndex] = nextBlock;
		}
	}
}
