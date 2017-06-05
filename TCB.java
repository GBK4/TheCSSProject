// ------------------------------------------------------------------------------------
// CSS 430 Operating System
// Program 5: File System
// Last update: 06/04/2017
// Luke Bushey
// Garret King
// Lan Yang
// ------------------------------------------------------------------------------------

public class TCB {
    private Thread thread = null;
    private int tid = 0;
    private int pid = 0;
    private boolean terminated = false;
    
    // User file descriptor table:
    // each entry pointing to a file (structure) table entry
    public FileTableEntry[] ftEnt = null;

    //Constructor
    public TCB(Thread newThread, int myTid, int parentTid) {
	thread = newThread;
	tid = myTid;
	pid = parentTid;
	terminated = false;

        //  The following code is added for the file system
	ftEnt = new FileTableEntry[32];
        for (int i = 0; i < 32; i++)
            ftEnt[i] = null;        // all the entries are initialized to null
        // fd[0], fd[1], and fd[3] are kept null.
    }

    //Returns the stored thread.
    public synchronized Thread getThread( ) {
	return thread;
    }

    //Returns the thread's id.
    public synchronized int getTid( ) {
	return tid;
    }

    //Returns the thread's parent's id.
    public synchronized int getPid( ) {
	return pid;
    }

    //Sets terminated to true.
    public synchronized boolean setTerminated( ) {
	terminated = true;
	return terminated;
    }

    //Returns the value of terminated.
    public synchronized boolean getTerminated( ) {
	return terminated;
    }

    // added for the file system
    //Finds a empty file descriptor for the entry.
    public synchronized int getFd( FileTableEntry entry ) {
	if ( entry == null )
	    return -1;
	for ( int i = 3; i < 32; i++ ) {
	    if ( ftEnt[i] == null ) {
		ftEnt[i] = entry;
		return i;
	    }
	}
	return -1;
    }

    // added for the file system
    //Finds the entry of the file descriptor and sets the file descriptor to null
    public synchronized FileTableEntry returnFd( int fd ) {
	if ( fd >= 3 && fd < 32 ) {
	    FileTableEntry oldEnt = ftEnt[fd];
	    ftEnt[fd] = null;
	    return oldEnt;
	}
	else
	    return null;
    }

    // added for the file system
    //Finds the entry based ont he file descriptor.
    public synchronized FileTableEntry getFtEnt( int fd ) {
	if ( fd >= 3 && fd < 32 )
	    return ftEnt[fd];
	else
	    return null;
    }
}
