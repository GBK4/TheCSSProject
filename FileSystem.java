// ------------------------------------------------------------------------------------
// CSS 430 Operating System
// Program 5: File System -- FileSystem -- description in design documentation
// Provided Code
// Luke Bushey
// Garret King
// Lan Yang
// ------------------------------------------------------------------------------------

  // objects needed for FS
  private SuperBlock superblock;
  private Directory directory;
  private FileTable filetable;
  
  // additional variables needed for seek
  private final int SEEK_SET = 0;
  private final int SEEK_CUR = 1;
  private final int SEEK_END = 2;

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
    FileTableEntry fte = open("/", "w");
    byte[] data = directory.directory2bytes();
    // then write to superblock
    write(fte, data);
    close(fte);   
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
  boolean close(FileTableEntry fte)
  {
    // synchronize all pending tasks prior to close
    synchronized (fte)
    {
      // decrement count of processes using file
      fte.count--;      
      if (fte.count == 0)
      {
        return filetable.ffree(fte);
      }
      // return true if other processes are still using file
      else
      {
        return true;
      }
    }
  }
  
  // fsize returns the size of FileTableEntry
  int fsize(FileTableEntry fte) 
  {
    // synchronize all pending tasks prior to returning size
    synchronized(fte)
    {
      return fte.inode.length;
    }
  }

  int read(FileTableEntry fte, byte[] buffer)
  {
    // don't allow if entry is being appended or written but return error
    if ((fte.mode == "a") || (fte.mode == "w"))
    {
      return -1;
    }
    // initialze count of bytes read
    int bytesRead = 0;
    int readLength = 0;
    int blockFound;
    int offset;
    // synchronize all pending tasks prior to read    
    synchronized (fte)
    {
      // initialize variables for reading file
      int bytesRemaining = buffer.length;
      // loop until file is fully read
      while ((bytesRemaining > 0) && (fte.seekPtr < fsize(fte)))
      {
        // find block that has the file
        blockFound = fte.inode.findTargetBlock(fte.seekPtr);
        // if block not found return error
        if (blockFound == -1)
        {
          return -1;
        }
        // assign offset to readLength
        offset = fte.seekPtr % Disk.blockSize;        
        // only read until end of currentBlock or file
        if ((readLength - offset) < (fsize(fte) - fte.seekPtr))
        {
          readLength = (Disk.blockSize - offset);
        }
        else
        {
          readLength = (fsize(fte) - fte.seekPtr);
        }
        // then control readLength to buffer size
        if (readLength > bytesRemaining)
        {
          readLength = bytesRemaining;
        }
        // copy data into the buffer from disk
        byte[] dataTemp = new byte[Disk.blockSize];
        SysLib.rawread(blockFound, dataTemp);
        System.arraycopy(dataTemp, offset, buffer, bytesRead, readLength);
        // then finally update counts
        bytesRead = (bytesRead + readLength);
        bytesRemaining = (bytesRemaining - readLength);
        fte.seekPtr = (fte.seekPtr + readLength);
      }
    }
    // return final count of bytes read
    return bytesRead;
  }

  int write(FileTableEntry fte, byte[] buffer) 
  {
    // don't allow if entry is being in read only mode but return error
    if (fte.mode == "r")
    {
      return -1;
    }
    // initialze count of bytes written
    int bytesWritten = 0;
    int writeLength;
    int blockFound;
    int offset;
    // synchronize all pending tasks prior to write
    synchronized(fte)
    {
      // initialize variables for reading file
      int bytesRemaining = buffer.length;
      // loop until file is fully written to disk
      while (bytesRemaining > 0)
      {
        // find block that has the file
        blockFound = fte.inode.findTargetBlock(fte.seekPtr);

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
            = fte.inode.registerTargetBlock(fte.seekPtr, (short)blockFound);
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
              if (!fte.inode.registerIndexBlock(location))
              {
                return -1;
              }
              // register target block again return error if disk full
              if (fte.inode.registerTargetBlock(fte.seekPtr, 
                (short)blockFound) < 0)
              {
                return -1;
              }
          }
        }
        // determine block offset
        offset = (fte.seekPtr % Disk.blockSize);        
        // write until end of block or end of file
        if ((Disk.blockSize - offset) < bytesRemaining)
        {
          writeLength = (Disk.blockSize - offset);
        }
        else 
        {
          writeLength = bytesRemaining;
        }
        // fetch data from buffer to write
        byte[] dataTemp = new byte[Disk.blockSize];
        SysLib.rawread(blockFound, dataTemp);
        System.arraycopy(buffer, bytesWritten, dataTemp, offset, writeLength);
        SysLib.rawwrite(blockFound, dataTemp);
        // then update counts
        bytesWritten = (bytesWritten + writeLength);
        bytesRemaining = (bytesRemaining - writeLength);
        fte.seekPtr = (fte.seekPtr + writeLength);
        // finally update inode
        if (fte.inode.length < fte.seekPtr)
        {
          fte.inode.length = fte.seekPtr;
        }       
      }     
    }
    // save changes back to disk and return count of bytes written
    fte.inode.toDisk(fte.iNumber);
    return bytesWritten;
  }
  
  // delete specified file per filename parameter
  boolean delete(String filename) 
  {
    FileTableEntry fte = open(filename, "w");
    short temp = fte.iNumber;
    return (close(fte)) && (directory.ifree(temp));
  }   

  // seek function to find file entry with offset and whence
  int seek(FileTableEntry fte, int offset, int whence) 
  {
    synchronized (fte)
    {
      switch (whence)
      {
        case SEEK_SET:
          if ((offset >= 0) && (offset <= fsize(fte)))
          {
            fte.seekPtr = offset; 
            break;
          }
        case SEEK_CUR:            
          if (((offset + fte.seekPtr) >= 0) 
            && ((offset + fte.seekPtr) <= fsize(fte)))
          {
            fte.seekPtr += offset; 
            break; 
          }
        case SEEK_END:  
          if (((offset + fsize(fte)) >= 0)
          	&& ((offset + fsize(fte)) <= fsize(fte)))
          {
            fte.seekPtr = (offset + fsize(fte));
            break;
          }
        default: 
          return -1;
      } 
      return fte.seekPtr; 
    }
  }
  
}
