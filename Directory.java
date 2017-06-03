/*
	Directory.java V1
	Luke Bushey
*/


public class Directory {
	private static int maxChars = 30; // max characters of each file name

	// Directory entries
	private int fsize[];        // each element stores a different file size.
	private char fnames[][];    // each element stores a different file name.

	public Directory( int maxInumber ) { // directory constructor
	  fsize = new int[maxInumber];     // maxInumber = max files
	  for ( int i = 0; i < maxInumber; i++ ) 
	      fsize[i] = 0;                 // all file size initialized to 0
	  fnames = new char[maxInumber][maxChars];
	  String root = "/";                // entry(inode) 0 is "/"
	  fsize[0] = root.length( );        // fsize[0] is the size of "/".
	  root.getChars( 0, fsize[0], fnames[0], 0 ); // fnames[0] includes "/"
	}

	// assumes data[] received directory information from disk
	// initializes the Directory instance with this data[]
	public int bytes2directory(byte data[])
	{
    // assumes data[] received directory information from disk
    for (int i = 0; i < fsize.length; i++)
    {
      fsize[i] = SysLib.bytes2int(data, i * 4);
    }    
		// inital entry character limit
    int entries = maxChars * 2;
    int blockOffset = (fsize.length - 1) * 4;
    String fileName;
    // loop through populating name
    for (int i = 0; i < fnames.length; i++)
    {
      fileName = new String(data, blockOffset, entries);
      blockOffset += 2;      
      fileName.getChars(0, fsize[i], fnames[i], 0);
    }    
    return 0;
	}

  // converts and return Directory information into a plain byte array
  // this byte array will be written back to disk
  // note: only meaningfull directory information should be converted
  // into bytes.
	public byte[] directory2bytes()
	{
		// 
    int entries = maxChars * 2;
    int fileAlloc = fsize.length * 4;    
    byte[] dataTemp = new byte[entries * fnames.length + fileAlloc];
    // loop through creating byteArray
    for (int i = 0; i < fsize.length; i++)
    {
      SysLib.int2bytes(fsize[i], dataTemp, i * 4);
    }
    
    int blockOffset = (fsize.length - 1) * 4;
    String fileName;    
    // converts directory information into a plain byte array to write to disk
    for (int i = 0; i < fnames.length; i++)
    {
      fileName = new String(fnames[i], 0, fsize[i]);
      byte[] buffer = fileName.getBytes();
      System.arraycopy(buffer, 0, dataTemp, blockOffset, buffer.length);
      blockOffset += entries;
    }    
    return dataTemp;
	}

	
	public short ialloc(String filename)
	{
    // control 30 character limit
    int nameLength;
    if (filename.length() > maxChars)
    {
    	nameLength = maxChars; // length set to maxChars if its larger
    }
    else
    {
    	nameLength = filename.length();
    }    
    // find and use the first available empty file
    for (short i = 0; i < fsize.length; i++)
    {
      if (fsize[i] == 0)
      {
        // use this one, empty file
        fsize[i] = nameLength;
        filename.getChars(0, nameLength, fnames[i], 0);
        return i;
      }
    }
    // return error if none available
    return -1;
	}

	// to free up directory entry
	public boolean ifree(short iNumber)
	{
		// valid entry
    if (fsize[iNumber] > 0)
    {
      // free up directory entry
      fsize[iNumber] = 0;
      return true;
    }    
    return false;
	}

	public short namei(String filename)
	{
		// string to compare against filename
    String temp;    
    for (short i = 0; i < fsize.length; i++) 
    {
    	// directory search
      if (fsize[i] == filename.length())  
      {
        temp = new String(fnames[i], 0, fsize[i]);
        if (filename.equals(temp))
        {
          // found iNumber, filename located
          return i;
        }
      }
    }
    // no match to filename found return -1
    return -1;
	}
}
