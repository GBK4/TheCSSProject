// ------------------------------------------------------------------------------------
// CSS 430 Operating System
// Program 5: File System
// Last update: 06/04/2017
// Luke Bushey
// Garret King
// Lan Yang
// ------------------------------------------------------------------------------------

import java.util.Vector;

public class FileTable 
{
    private final static int UNUSED = 0;
    private final static int USED = 1;
    private final static int READ = 2;
    private final static int WRITE = 3;
    private final static int DELETE = 4;
    
    private Vector<FileTableEntry> table;   
    private Directory dir;

    public FileTable(Directory directory) 
    {
        table = new Vector<FileTableEntry>(); 
        dir = directory;      
    } 

    public synchronized FileTableEntry falloc(String filename, String mode) 
    {
        short inumber = -1; 
        Inode inode = null;

        while (true) 
        {
            if (filename.equals("/"))
            {
                inumber = (short)0;
            }
            else
            {
                inumber = dir.namei(filename);
            }
            if (inumber < 0) 
            {
                if (mode.equals("r"))
                {
                    return null;
                }
                else
                {
                    inumber = dir.ialloc(filename);
                    inode = new Inode(inumber);
                    inode.flag = WRITE;
                    break;
                }
            } 
            else 
            {
                inode = new Inode(inumber);
                if (mode.equals("r"))
                {
                    if (inode.flag == WRITE)
                    {
                        try {
                            wait();
                        } catch (InterruptedException e) { }
                    }
                    else
                    {
                        inode.flag = READ;
                        break;
                    }
                }
                else
                {
                    if (inode.flag == USED || inode.flag == UNUSED) 
                    {
                        inode.flag = WRITE;
                        break;
                    } 
                    else 
                    {
                        try {
                            wait();
                        } catch (InterruptedException e) { }
                    }
                }
            }
        }

        inode.count++;
        inode.toDisk(inumber);
        FileTableEntry entry = new FileTableEntry(inode, inumber, mode);
        table.addElement(entry);
        return entry;
    }
    
    public synchronized boolean ffree(FileTableEntry entry) 
    {
        if (table.remove(entry))
        {
            Inode inode = new Inode(entry.iNumber);
            notifyAll();
            inode.flag = USED;
            inode.count--;
            inode.toDisk(entry.iNumber);
            return true;
        }
        else
        {
            return false;
        }
    }

    public synchronized boolean fempty() 
    {
        return table.isEmpty();
    }
}
