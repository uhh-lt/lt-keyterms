package uhh_lt.keyterms;

public class Type {

	private String pos;
	private String value;
	private Long count = 1L;
	private Double significance = .0;
	
	public Type(String value, Long count, String pos) {
		super();
		this.value = value;
		this.count = count;
		this.pos = pos;
	}
	
	public String getPos() {
		return pos;
	}
	public void setPos(String pos) {
		this.pos = pos;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public Long getCount() {
		return count;
	}
	public void setCount(Long count) {
		this.count = count;
	}
	
	
	
}
