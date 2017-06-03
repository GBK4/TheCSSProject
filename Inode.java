

public class Inode
{
    private final static int iNodeSize = 32;        // fixed to 32 bytes
    private final static int directSize = 11;       // the number of direct pointers
    private final static int iNodeNumPerBlock = 16; // default node number per block

    private final static short UNUSED = -1;
    private final static short UNREGISTERED = -1;

    public int length;      // file size in bytes
    public short count;     // the number of file-table entries pointing to this
    public short flag;      // 0 = unused, 1 = used, 2 = read, 3 = write, 4 = delete
    public short direct[] = new short[directSize];  // direct pointers
    public short indirect;  // an indirect pointer

    public Inode()
    {
        length = 0;
        count = 0;
        flag = 1;
        for(int i = 0; i < directSize; i++)
        {
            direct[i] = -1;
        }
        indirect = -1;
    }

    public Inode(short iNumber)
    {
        int blkNumber = iNumber / iNodeNumPerBlock + 1;
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(blkNumber, data);
        int offset = (iNumber % iNodeNumPerBlock) * iNodeSize;
        
        length = SysLib.bytes2int(data, offset);
        offset += 4;
        count = SysLib.bytes2short(data, offset);
        offset += 2;
        flag = SysLib.bytes2short(data,offset);
        offset += 2;

        for(int i = 0; i < directSize; i++)
        {
            direct[i] = SysLib.bytes2short(data, offset);
            offset += 2;
        }
        indirect = SysLib.bytes2short(data, offset);
    }

    public void toDisk(short iNumber)
    {
        if(iNumber < 0)
            return;

        int blkNumber = iNumber / iNodeNumPerBlock + 1;
        byte[] buffer = new byte[Disk.blockSize];

        SysLib.rawread(blkNumber, buffer);
        int offset = (iNumber % iNodeNumPerBlock) * iNodeSize;

        SysLib.int2bytes(length, buffer, offset);
        offset += 4;
        SysLib.short2bytes(count, buffer, offset);
        offset += 2;
        SysLib.short2bytes(flag, buffer, offset);
        offset += 2;

        for(int i = 0; i < directSize; i++)
        {
            SysLib.short2bytes(direct[i], buffer, offset);
            offset += 2;
        }

        SysLib.short2bytes(indirect, buffer, offset);
        SysLib.rawwrite(blkNumber, buffer);
    }

    public int findIndexBlock()
    {
        return indirect;
    }

    public boolean registerIndexBlock(short indexBlockNumber)
    {
        if(indexBlockNumber < 0)
            return false;
        if(indirect != UNUSED)
            return false;
        
        for(int i = 0; i < directSize; i++)
        {
            if(direct[i] == UNUSED)
                return false;
        }

        indirect = indexBlockNumber;
        byte[] data = new byte[Disk.blockSize];

        for(int i = 0; i < (Disk.blockSize / 2); i++)
        {
            SysLib.short2bytes((short)-1, data, i*2);
        }

        SysLib.rawwrite(indexBlockNumber, data);
        return true;
    }

    public int findTargetBlock(int offset)
    {
        int target = offset / Disk.blockSize;

        if(target < directSize)
            return direct[target];
        if(indirect < 0)
            return -1;

        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(indirect, data);

        int iBlock = (target - directSize) * 2;
        return SysLib.bytes2short(data, iBlock);
    }

    public int registerTargetBlock(int offset, short targetBlockNumber)
    {
        int target = offset / Disk.blockSize;

        // Register into one direct block
        if(target < directSize)
        {
            if(direct[target] >= 0)
                return -1;
            if((target > 0) && (direct[target - 1] == UNUSED))
                return -1;
            
            direct[target] = targetBlockNumber;
            return 0;
        }

        if(indirect < 0)
            return -1;
        else
        {
            byte[] data = new byte[Disk.blockSize];
            SysLib.rawread(indirect, data);

            int iBlock = (target - directSize) * 2;
            if(SysLib.bytes2short(data, iBlock) > 0)
                return -1;
            else
            {
                SysLib.short2bytes(targetBlockNumber, data, iBlock);
                SysLib.rawwrite(indirect, data);
            }
        }
        return 0;
    }

    public byte[] unregisterIndexBlock()
    {
        if(indirect == UNUSED)
            return null;

        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(indirect, data);

        indirect = -1;
        return data;
    }
}