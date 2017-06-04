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
  
  // bytes2directory assumes data[] received directory information from disk
  // initializes the Directory instance with this data[]
  public int bytes2directory(byte data[])
  {    
    for (int i = 0; i < fsize.length; i++)
    {
      fsize[i] = SysLib.bytes2int(data, i * 4);
    }    
    // inital entry character limit
    int nameLength = (maxChars * 2);
    int offset = ((fsize.length - 1) * 4);
    String fileName;
    // loop through populating name
    for (int i = 0; i < fnames.length; i++)
    {
      fileName = new String(data, offset, nameLength);
      offset += 2;      
      fileName.getChars(0, fsize[i], fnames[i], 0);
    }    
    return 0;
  }

  // directory2bytes converts and return Directory information into a plain byte array
  // this byte array will be written back to disk
  // note: only meaningfull directory information should be converted
  // into bytes.
  public byte[] directory2bytes()
  { 
    // loop through creating byteArray
    int nameLength = (maxChars * fsize.length * 2);
    int sizeLength = (fsize.length * 4);    
    int offset = 0;
    byte[] dataTemp = new byte[nameLength + sizeLength]; 
    for (int i = 0; i < fsize.length; i++)
    {
      SysLib.int2bytes(fsize[i], dataTemp, offset);
      offset += 4;
    }    
    // then store directory info into plain byte array
    for (int i = 0; i < fnames.length; i++)
    {
      String fileName = new String(fnames[i], 0, fsize[i]);
      byte[] nameBytes = fileName.getBytes();
      System.arraycopy(nameBytes, 0, dataTemp, offset, nameBytes.length);
      offset += (maxChars * 2);
    }    
    return dataTemp;
  }

  // ialloc allocates new inode for the filename
  public short ialloc(String filename)
  {
    // control 30 character limit
    int nameLength;
    if (filename.length() > maxChars)
    {
      nameLength = maxChars; 
    }
    else
    {
      nameLength = filename.length();
    }    
    // find first available entry
    for (short i = 0; i < fsize.length; i++)
    {
      if (fsize[i] == 0)
      {
        fsize[i] = nameLength;
        filename.getChars(0, nameLength, fnames[i], 0);
        return i;
      }
    }
    // return error if no inode available
    return -1;
  }

  // ifree to free up directory entry
  public boolean ifree(short iNumber)
  {
    // confirm valid entry
    if ((fsize[iNumber] > 0) && (iNumber > 0))
    {
      // free up directory entry
      fsize[iNumber] = 0;
      return true;
    }    
    return false;
  }
  
  // namei to find filename in directory
  public short namei(String filename)
  {  
    // directory search
    for (short i = 0; i < fsize.length; i++) 
    {
      String compare = new String(fnames[i], 0, fsize[i]);      
      if (filename.equals(compare) && (fsize[i] == filename.length()) )  
      {
        return i;
      }
    }
    // file not found then return error
    return -1;
  }
}
