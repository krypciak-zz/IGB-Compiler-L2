package me.krypek.igb.cl2.datatypes;

import static me.krypek.igb.cl1.datatypes.Instruction.Add;
import static me.krypek.igb.cl1.datatypes.Instruction.Copy;
import static me.krypek.igb.cl1.datatypes.Instruction.Math;
import static me.krypek.igb.cl1.datatypes.Instruction.Math_CC;

import java.util.ArrayList;

import me.krypek.igb.cl1.datatypes.Instruction;
import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl2.solvers.EqSolver;
import me.krypek.utils.Pair;
import me.krypek.utils.TripleObject;
import me.krypek.utils.Utils;

public class Array {

	public final int cell;
	public final int[] size;
	public final int totalSize;

	public Array(int cell, int[] size, int totalSize) {
		this.cell = cell;
		this.size = size;
		this.totalSize = totalSize;
	}

	@Override
	public String toString() { return "|" + cell + "| " + Utils.arrayToString(size, '[', ']') + " (" + totalSize + ")"; }

	public TripleObject<Boolean, Integer, ArrayList<Instruction>> getArrayCell(EqSolver eqsolver, Field[] dims, int outCell) {
		if(dims.length != size.length)
			throw Err.normal("Expected " + size.length + " array dimensions, insted got " + dims.length + ".");

		boolean isAllVal = true;
		int cell = 0;

		ArrayList<Pair<Integer, Integer>> cellList = new ArrayList<>();
		for (int i = dims.length - 1, x = 1; i >= 0; x *= size[i--]) {
			Field f = dims[i];
			if(!f.isVal()) {
				isAllVal = false;
				cellList.add(new Pair<>(i, x));
			} else {
				double val = f.value;
				if(val % 1 != 0)
					throw Err.normal("Array dimension has to be an int, insted got: \"" + val + "\".");
				int val1 = (int) val;
				if(val1 >= size[i])
					throw Err.normal("Index out of bounds: " + val1 + " out of " + size[i] + ".");

				cell += val1 * x;
			}
		}
		ArrayList<Instruction> list = new ArrayList<>();
		if(isAllVal)
			return new TripleObject<>(false, this.cell + cell, list);

		final int len_ = dims.length - 1;
		if(cellList.size() == 1) {

			var pair = cellList.get(0);
			int i = pair.getFirst();
			double x = pair.getSecond();

			Field f = dims[i];
			ArrayList<Instruction> listToAdd;
			if(cell == 0) {
				if(i == len_) {
					Equation neweq = new Equation(new char[] { '+' }, new Field[] { f, new Field((double) this.cell) });
					listToAdd = eqsolver.getInstructionListFromEq(neweq, outCell);
				} else {
					Equation neweq = new Equation(new char[] { '+' },
							new Field[] { new Field((double) this.cell), new Field(new Equation(new char[] { '*' }, new Field[] { f, new Field(x) })) });
					listToAdd = eqsolver.getInstructionListFromEq(neweq, outCell);
				}
			} else if(i == len_) {
				Equation neweq = new Equation(new char[] { '+', }, new Field[] { f, new Field((double) (this.cell + cell)) });
				listToAdd = eqsolver.getInstructionListFromEq(neweq, outCell);
			} else {
				Equation neweq = new Equation(new char[] { '*', '+', '+' }, new Field[] { f, new Field(x), new Field((double) (cell + this.cell)) });
				listToAdd = eqsolver.getInstructionListFromEq(neweq, outCell);
			}
			list.addAll(listToAdd);
			return new TripleObject<>(true, outCell, list);
		}

		boolean set = false;
		boolean waitingForNext = false;
		for (int h = 0; h < cellList.size(); h++) {
			Pair<Integer, Integer> pair1 = cellList.get(h);
			int i = pair1.getFirst();
			int x = pair1.getSecond();
			Field f = dims[h];
			if(!f.isVal())
				if(set) {
					Equation neweq = new Equation(new char[] { '*', '+' }, new Field[] { dims[h - 1], new Field((double) x), new Field(outCell) });
					ArrayList<Instruction> solved = eqsolver.getInstructionListFromEq(neweq, outCell);
					list.addAll(solved);

				} else {
					if(waitingForNext) {
						waitingForNext = false;
						set = true;
						Equation neweq = new Equation(new char[] { '*', '+', }, new Field[] { dims[h - 1], new Field((double) x), f });
						ArrayList<Instruction> solved = eqsolver.getInstructionListFromEq(neweq, outCell);
						list.addAll(solved);

						continue;
					}

					if(cell == 0 && i == len_) {
						waitingForNext = true;
						continue;
					}
					var pair2 = eqsolver.getInstructionsFromField(f);
					int cell1 = pair2.getFirst();
					list.addAll(pair2.getSecond());
					if(cell == 0)
						list.add(Math("*", cell1, false, x, outCell));
					else if(i == len_)
						list.add(Add(cell1, false, cell, outCell));
					else {
						list.add(Math("*", cell1, false, x, outCell));
						list.add(Add(outCell, false, cell, outCell));
					}
					set = true;
				}
		}
		list.add(Add(outCell, false, this.cell, outCell));
		return new TripleObject<>(true, outCell, list);
	}

	public ArrayList<Instruction> getAccess(EqSolver eqs, Field[] dims, int outCell) {
		var obj = getArrayCell(eqs, dims, outCell);
		if(!obj.getValue1())
			return Utils.listOf(Copy(obj.getValue2(), outCell));

		ArrayList<Instruction> list = obj.getValue3();
		list.add(Math_CC(obj.getValue2(), outCell));

		return list;
	}
}
