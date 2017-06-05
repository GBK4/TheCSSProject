// ------------------------------------------------------------------------------------
// CSS 430 Operating System
// Program 5: File System -- FileTable -- description in design documentation
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

    //Constructor
    public FileTable(Directory directory) 
    {
        table = new Vector<FileTableEntry>(); 
        dir = directory;      
    } 

    //A method to allocated a new file table entry based on filename and mode.
    public synchronized FileTableEntry falloc(String filename, String mode) 
    {
        short inumber = -1; 
        Inode inode = null;

        while (true) 
        {
            //Check if the filename is directory or if it exists.
            if (filename.equals("/"))
            {
                inumber = (short)0;
            }
            else
            {
                inumber = dir.namei(filename);
            }
            //If the file does not exist check if the mode is read.
            if (inumber < 0)
            {
                if (mode.equals("r"))
                {
                    //If the mode is read, then a new entry shouldn't be created.
                    return null;
                }
                else
                {
                    //Create a new inode for the new entry.
                    inumber = dir.ialloc(filename);
                    inode = new Inode(inumber);
                    inode.flag = WRITE;
                    break;
                }
            } 
            else 
            {
                inode = new Inode(inumber);
                //If the mode is read and flag is write, then wait, otherwise 
                //set the flag to read.
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
                    //If the flag is used or unused, set the flag to write.
                    if (inode.flag == USED || inode.flag == UNUSED) 
                    {
                        inode.flag = WRITE;
                        break;
                    } 
                    else 
                    {
                        //Otherwise the inode is being read and the thread needs
                        //to wait.
                        try {
                            wait();
                        } catch (InterruptedException e) { }
                    }
                }
            }
        }

        //Set up the new entry.
        inode.count++;
        inode.toDisk(inumber);
        FileTableEntry entry = new FileTableEntry(inode, inumber, mode);
        table.addElement(entry);
        return entry;
    }
    
    //A method to free a filetable entry from the table..
    public synchronized boolean ffree(FileTableEntry entry) 
    {
        if (table.remove(entry)) //Check if entry is in table and remove it.
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

    //A method to check if the table is empty.
    public synchronized boolean fempty() 
    {
        return table.isEmpty();
    }
}
