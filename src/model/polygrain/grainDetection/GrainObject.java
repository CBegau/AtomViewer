package model.polygrain.grainDetection;

class GrainObject {
	private static int numberSource = 0; 
	
	private int number;
	
	public GrainObject() {
		number = getNextNumber();
	}
	
	public static int getNumTotalCreated(){
		return numberSource;
	}
	
	public int getNumber() {
		return number;
	}
	
	public void overwriteNumber(GrainObject grain) {
		this.number = grain.number;
	}
	
	private static synchronized int getNextNumber(){
		return numberSource++;
	}
	
	static void init(){
		numberSource=0;
	}
}
