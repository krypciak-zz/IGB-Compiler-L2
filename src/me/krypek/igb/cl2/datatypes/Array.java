package me.krypek.igb.cl2.datatypes;

import static me.krypek.igb.cl1.Instruction.*;

import java.util.ArrayList;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.IGB_CL2_Exception;
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
	public String toString() { return cell + "c " + totalSize + " == " + Utils.arrayToString(size, '[', ']'); }

	public TripleObject<Boolean, Integer, ArrayList<Instruction>> getArrayCell(EqSolver eqs, Field[] dims, int outCell) {
		if(dims.length != size.length)
			throw new IGB_CL2_Exception("Expected " + size.length + " array dimensions, insted got " + dims.length + ".");

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
					throw new IGB_CL2_Exception("Array dimension has to be an int, insted got: \"" + val + "\".");
				int val1 = (int) val;
				if(val1 >= size[i])
					throw new IGB_CL2_Exception("Index out of bounds: " + val1 + " out of " + size[i] + ".");

				cell += val1 * x;
			}
		}
		ArrayList<Instruction> list = new ArrayList<>();
		if(isAllVal)
			return new TripleObject<>(true, cell, list);

		final int len_ = dims.length - 1;
		if(cellList.size() == 1) {

			var pair = cellList.get(0);
			int i = pair.getFirst();
			int x = pair.getSecond();

			Field f = dims[i];
			if(cell == 0) {
				if(i == len_) {
					var pair1 = eqs.getInstructionsFromField(f, outCell);
					list.addAll(pair1.getSecond());
				} else {
					var pair1 = eqs.getInstructionsFromField(f);
					list.addAll(pair1.getSecond());
					list.add(Math("*", pair1.getFirst(), false, x, outCell));
				}
			} else if(i == len_) {
				var pair1 = eqs.getInstructionsFromField(f, outCell);
				list.addAll(pair1.getSecond());
				list.add(Add(outCell, false, cell, outCell));
			} else {
				var pair1 = eqs.getInstructionsFromField(f, -1);
				list.addAll(pair1.getSecond());
				list.add(Math("*", pair1.getFirst(), false, x, outCell));
				list.add(Add(outCell, false, cell, outCell));
			}
			return new TripleObject<>(false, outCell, list);
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
					var pair2 = eqs.getInstructionsFromField(dims[h - 1]);
					int cell2 = pair2.getFirst();
					list.addAll(pair2.getSecond());
					list.add(Math("*", cell2, false, x, IGB_MA.TEMP_CELL_2));
					list.add(Add(outCell, true, IGB_MA.TEMP_CELL_2, outCell));

				} else {
					if(waitingForNext) {
						waitingForNext = false;
						set = true;

						var prevPair = eqs.getInstructionsFromField(dims[h - 1], IGB_MA.TEMP_CELL_3);
						list.addAll(prevPair.getSecond());

						var currPair = eqs.getInstructionsFromField(f, IGB_MA.TEMP_CELL_2);
						list.addAll(currPair.getSecond());
						list.add(Math("*", IGB_MA.TEMP_CELL_2, false, x, IGB_MA.TEMP_CELL_2));
						list.add(Add(IGB_MA.TEMP_CELL_2, true, IGB_MA.TEMP_CELL_3, outCell));
						continue;
					}

					if(cell == 0 && i == len_) {
						waitingForNext = true;
						continue;
					}
					var pair2 = eqs.getInstructionsFromField(f);
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
		System.out.println(new TripleObject<>(false, outCell, list));
		return new TripleObject<>(false, outCell, list);
	}

	public ArrayList<Instruction> getAccess(EqSolver eqs, Field[] dims, int outCell) {
		var obj = getArrayCell(eqs, dims, outCell);
		ArrayList<Instruction> list = obj.getValue3();
		if(obj.getValue1()) {
			list.add(Copy(cell, outCell));
			return list;
		}

		list.add(Math_CC(obj.getValue2(), outCell));
		return list;
	}

	public Pair<Integer, ArrayList<Instruction>> getAccess(EqSolver eqs, int tempCell, Field[] dims) {
		var obj = getArrayCell(eqs, dims, tempCell);
		ArrayList<Instruction> list = obj.getValue3();
		if(obj.getValue1())
			// list.add(Copy(cell, outCell));
			return new Pair<>(tempCell, list);

		list.add(Math_CC(obj.getValue2(), tempCell));
		return new Pair<>(tempCell, list);
	}

	public ArrayList<Instruction> getWrite(EqSolver eqs, Field[] dims, double value) {
		var obj = getArrayCell(eqs, dims, IGB_MA.TEMP_CELL_3);
		ArrayList<Instruction> list = obj.getValue3();
		list.add(Init(value, IGB_MA.TEMP_CELL_2));
		list.add(Math_CW(IGB_MA.TEMP_CELL_3, IGB_MA.TEMP_CELL_2));
		return list;
	}

	public ArrayList<Instruction> getWrite(EqSolver eqs, Field[] dims, int cell) {
		var obj = getArrayCell(eqs, dims, IGB_MA.TEMP_CELL_3);
		ArrayList<Instruction> list = obj.getValue3();
		list.add(Math_CW(IGB_MA.TEMP_CELL_3, cell));
		return list;
	}

}
