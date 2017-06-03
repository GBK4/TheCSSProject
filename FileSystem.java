/*
	FileSystem.java V1
	Luke Bushey
*/

public class FileSystem {
	// objects needed for FS
	private SuperBlock superblock;
	private Directory directory;
	private FileTable filetable;
	
	// additional variables needed for seek
	private final int SEEK_SET = 0;
	private final int SEEK_CUR = 1;
	private final int SEEK_END = 2;
	
	// defining blockSize for 512 bytes
	private final int blockSize = 512;

	// constructor for FileSystem obj
	public FileSystem(int diskBlocks) 
	{
	  // create superblock, and format disk with 64 inodes in default
	  superblock = new SuperBlock(diskBlocks);
	  // create directory, and register "/" in directory entry 0
	  directory = new Directory(superblock.inodeBlocks);
	  // file table is created, and store directory in the file table
	  filetable = new FileTable(directory);

	  // directory reconstruction
	  FileTableEntry dirEnt = open("/", "r");
	  int dirSize = fsize(dirEnt);
	  if (dirSize > 0) 
	  {
	     byte[] dirData = new byte[dirSize];
	     read(dirEnt, dirData);
	     directory.bytes2directory(dirData);
	  }
	  close(dirEnt);
	}
	
	// sync function to write files into superblock
	void sync() 
	{
		// initialize entry and data 
		FileTableEntry ftEnt = open("/", "w");
		byte[] data = directory.directory2bytes();
		// then write to superblock
		write(ftEnt, data);
		close(ftEnt);		
		superblock.sync();
	}

	// format function formats the disk
	boolean format(int files) 
	{
		// check for number of inodes is greater than 0
		if (files > 0)
		{
			superblock.format(files);
			directory = new Directory(superblock.inodeBlocks);
			filetable = new FileTable(directory);			
		}
		// else return error
		else
		{
			return false;
		}
		// return success
		return true;
	}

	// open function finds file entry per filename parameter
	FileTableEntry open(String filename, String mode) 
	{
		return filetable.falloc(filename,mode);
	}
	
	// close funtion closes file entry per FileTableEntry parameter
	boolean close(FileTableEntry ftEnt)
	{
		// synchronize all pending tasks prior to close
		synchronized (ftEnt)
		{
			// decrement count of processes using file
			ftEnt.count--;			
			// if there are still processes using the file return true
			if (ftEnt.count > 0)
			{
				return true;
			}
			else if (ftEnt.count == 0)
			{
				return filetable.ffree(ftEnt);
			}
			else
			{
				return false;
			}	
		}
	}
	
	// fsize returns the size of FileTableEntry
	int fsize(FileTableEntry ftEnt) 
	{
		// synchronize all pending tasks prior to returning size
		synchronized(ftEnt)
		{
			return ftEnt.inode.length;
		}
	}

	int read(FileTableEntry ftEnt, byte[] buffer)
	{
		// don't allow if entry is being appended or written but return error
		if ((ftEnt.mode == "a") || (ftEnt.mode == "w"))
		{
			return -1;
		}
		// initialze count of bytes read
		int bytesRead = 0;
		int readLength = 0;
		int blockFound;
		int blockOffset;
		// synchronize all pending tasks prior to read		
		synchronized (ftEnt)
		{
			// initialize variables for reading file
			int bytesRemaining = buffer.length;
			// loop until file is fully read
			while ((bytesRemaining > 0) && (ftEnt.seekPtr < fsize(ftEnt)))
			{
				// find block that has the file
				blockFound = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
				// if block not found return error
				if (blockFound == -1)
				{
					return -1;
				}
				// assign offset to readLength
				blockOffset = ftEnt.seekPtr % blockSize;				
				// only read until end of currentBlock or file
				if ((readLength - blockOffset) < (fsize(ftEnt) - ftEnt.seekPtr))
				{
					readLength = blockSize - blockOffset;
				}
				else
				{
					readLength = fsize(ftEnt) - ftEnt.seekPtr;
				}
				// then control readLength to buffer size
				if (readLength > bytesRemaining)
				{
					readLength = bytesRemaining;
				}
				// finally copy data into the buffer from disk
				byte[] dataTemp = new byte[blockSize];
				SysLib.rawread(blockFound, dataTemp);
				System.arraycopy(dataTemp, blockOffset, buffer, bytesRead, readLength);
				// then update counts
				bytesRead = bytesRead + readLength;
				bytesRemaining = bytesRemaining - readLength;
				ftEnt.seekPtr = ftEnt.seekPtr + readLength;
			}
		}
		// return final count of bytes read
		return bytesRead;
	}

	int write(FileTableEntry ftEnt, byte[] buffer) 
	{
		// don't allow if entry is being in read only mode but return error
		if (ftEnt.mode == "r")
		{
			return -1;
		}
		// initialze count of bytes written
		int bytesWritten = 0;
		int writeLength;
		int blockFound;
		int blockOffset;
		// synchronize all pending tasks prior to write
		synchronized(ftEnt)
		{
			// initialize variables for reading file
			int bytesRemaining = buffer.length;
			// loop until file is fully written to disk
			while (bytesRemaining > 0)
			{
				// find block that has the file
				blockFound = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
				// if no block found, get a free block
				if (blockFound == -1)
				{
					blockFound = (short)superblock.getFreeBlock();
					// return error if there aren't any free blocks available
					if (blockFound == -1)
					{
						return -1;
					}
					// else register new block
					int register 
						= ftEnt.inode.registerTargetBlock(ftEnt.seekPtr, (short)blockFound);
					// determine register case:
					switch (register)
					{
						case 0:
							break;
						// error conditions
						case -1:
						case -2:
							return -1;
						case -3:
							// create location of index
							short location = (short)superblock.getFreeBlock();
							// error if no free blocks found
							if (location < 0)
							{
								return -1;
							}
							// error if cannot create the index block
							if (!ftEnt.inode.registerIndexBlock(location))
							{
								return -1;
							}
							// register target block again return error if disk full
							if (ftEnt.inode.registerTargetBlock(ftEnt.seekPtr, 
								(short)blockFound) < 0)
							{
								return -1;
							}
					}
				}
				// determine block offset
				blockOffset = ftEnt.seekPtr % blockSize;				
				// write until end of block or end of file
				if ((blockSize - blockOffset) < bytesRemaining)
				{
					writeLength = blockSize - blockOffset;
				}
				else 
				{
					writeLength = bytesRemaining;
				}
				// fetch data from buffer to write
				byte[] dataTemp = new byte[blockSize];
				SysLib.rawread(blockFound, dataTemp);
				System.arraycopy(buffer, bytesWritten, dataTemp, blockOffset, writeLength);
				SysLib.rawwrite(blockFound, dataTemp);
				// then update counts
				bytesWritten = bytesWritten + writeLength;
				bytesRemaining = bytesRemaining - writeLength;
				ftEnt.seekPtr = ftEnt.seekPtr + writeLength;
				// finally update inode
				if (ftEnt.inode.length < ftEnt.seekPtr)
				{
					ftEnt.inode.length = ftEnt.seekPtr;
				}				
			}			
		}
		// save changes back to disk and return count of bytes written
		ftEnt.inode.toDisk(ftEnt.iNumber);
		return bytesWritten;
	}
	
	// delete specified file per filename parameter
	boolean delete(String filename) 
	{
		FileTableEntry ftEnt = open(filename, "w");
		short temp = ftEnt.iNumber;
		return (close(ftEnt)) && (directory.ifree(temp));
	}   

	int seek(FileTableEntry ftEnt, int offset, int whence) 
	{
		synchronized (ftEnt)
    {
	    switch(whence)
	    {
        // Offset is relative to the beginning of the file
        case SEEK_SET:
        	ftEnt.seekPtr = offset; 
        	break;

        // Offset is relative to the current seek pointer
        case SEEK_CUR:	ftEnt.seekPtr += offset;
	        if (ftEnt.seekPtr < 0) 
	        {
	        	// negative seekpointer set to 0, cant be negative
	        	ftEnt.seekPtr = 0
	        }

	        if (ftEnt.seekPtr > ftEnt.inode.length) 
	        {
	        	// seekpointer can be larger then file, if it is, sets Ptr to max file size
	        	ftEnt.seekPtr = ftEnt.inode.length;	
	        }
	        break;

        // block offset is relative to the end of the file
        case SEEK_END:	ftEnt.seekPtr = ftEnt.inode.length + offset;
	        if (ftEnt.seekPtr < 0) 
	        {
	        	ftEnt.seekPtr = 0;
	        }
	        // check if pointer is larger then inode length
	        if (ftEnt.seekPtr > ftEnt.inode.length) 
	        {
	        	ftEnt.seekPtr = ftEnt.inode.length;	// set seekPtr to inode lenght (end of file)
	        }
	        break;
        // Error on whence, it is neither of the 3 cases
        default: 
        	return -1;
	    }	
	    // return pointer if succesfull
	    return ftEnt.seekPtr;	
    }
	}
	
}
