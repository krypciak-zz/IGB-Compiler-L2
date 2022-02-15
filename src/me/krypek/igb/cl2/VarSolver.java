package me.krypek.igb.cl2;

public class VarSolver {
	private final IGB_CL2 cl2;
	private final RAM ram;

	public VarSolver(IGB_CL2 cl2) {
		this.cl2 = cl2;
		this.ram = cl2.getRAM();
	}

}
