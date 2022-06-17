package me.krypek.igb.cl2;

import java.util.ArrayList;
import java.util.HashSet;

public class Precompilation {

	public final Functions functions;
	public final PrecompilationFile[] precfA;

	private ArrayList<PrecompilationFile> precfList;
	private HashSet<String> processed;

	public Precompilation(String mainPath, boolean quiet) {
		functions = new Functions();

		precfList = new ArrayList<>();
		processed = new HashSet<>();

		processFile(mainPath);

		precfA = precfList.toArray(PrecompilationFile[]::new);
	}

	private void processFile(String path) {
		if(processed.contains(path))
			return;

		PrecompilationFile precf = new PrecompilationFile(path, functions);
		precfList.add(precf);

		precf.dependencies.forEach(path1 -> processFile(path1));
	}

}
