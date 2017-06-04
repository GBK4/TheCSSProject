
public class SuperBlock
{
    private final static int defaultInodeBlocks = 64;   // the number of blocks including inodes
    private final static int iNodeSize = 32;            // the size of each inode              

    public int totalBlocks;    // the number of disk blocks
    public int inodeBlocks;    // the number of inodes
    public int freeList;       // the block number of the free list's head

    public SuperBlock(int diskSize)
    {
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.rawread(0, superBlock);

        totalBlocks = SysLib.bytes2int(superBlock, 0);
        inodeBlocks = SysLib.bytes2int(superBlock, 4);
        freeList = SysLib.bytes2int(superBlock, 8);

        if(totalBlocks == diskSize && inodeBlocks > 0 && freeList >= 2)
            return;
        else
        {
            totalBlocks = diskSize;
            SysLib.cerr("Formatting\n");
            format(defaultInodeBlocks);
        }
    }

    // Write totaoBlocks, inodeBlocks, freeList to Disk
    public void sync()
    {
        byte[] block = new byte[Disk.blockSize];

        SysLib.int2bytes(totalBlocks, block, 0);
        SysLib.int2bytes(inodeBlocks, block, 4);
        SysLib.int2bytes(freeList, block, 8);

        SysLib.rawwrite(0, block);
    }

    // Initialize inodes, free blocks
    public void format(int numBlocks)
    {
        if (numBlocks < 0)
        {
            numBlocks = defaultInodeBlocks;
        }
        
        inodeBlocks = numBlocks;

        for(short i = 0; i < inodeBlocks; i++)
        {
            Inode node = new Inode();
            node.toDisk(i);
        }

        freeList = ((inodeBlocks * iNodeSize) / (Disk.blockSize)) + 1;
        
        for(int i = freeList; i < 1000; i++)
        {
            byte[] block = new byte[Disk.blockSize];

            SysLib.int2bytes(i+1, block, 0);
            SysLib.rawwrite(i, block);
        }

        sync();
    }

    // Dequeue top block in freeList
    public int getFreeBlock()
    {
        if(freeList <= 0 || freeList >= totalBlocks)
            return -1;

        int freeBlock = freeList;
        byte[] block = new byte[Disk.blockSize];

        SysLib.rawread(freeList, block);
        
        freeList = SysLib.bytes2int(block, 0);

        return freeBlock;
    }

    //Enqueue oldBlockNumber to top of freeList
    public boolean returnBlock(int oldBlockNumber)
    {
        if(oldBlockNumber <= 0 || oldBlockNumber >= totalBlocks)
            return false;
        
        byte[] block = new byte[Disk.blockSize];

        SysLib.int2bytes(freeList, block, 0);
        SysLib.rawwrite(oldBlockNumber, block);

        freeList = oldBlockNumber;

        return true;
    }
}