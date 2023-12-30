import java.io.Serializable;
import java.util.Date;

public class vFile implements Serializable {
	private String name;
	private int identifier;
	private String location;
	private String type;
	private long size;
	private int numOfBlocks;
	private byte protection;
	private Date creationTime;
	private Date modificationTime;
	private Date accessTime;
	public String getName() {
		return name;
	}
}
