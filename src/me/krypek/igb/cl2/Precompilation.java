package me.krypek.igb.cl2;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

public class Precompilation {

	public final Functions functions;
	public final PrecompilationFile[] precfA;

	private final ArrayList<PrecompilationFile> precfList;
	private final HashSet<String> processed;
	private final String mainPath;

	public Precompilation(String mainPath) {
		this.mainPath = mainPath;

		functions = new Functions();

		precfList = new ArrayList<>();
		processed = new HashSet<>();

		processFile(new File(mainPath).getAbsolutePath());

		precfA = precfList.toArray(PrecompilationFile[]::new);
	}

	private void processFile(String path) {
		if(processed.contains(path))
			return;

		processed.add(path);
		PrecompilationFile precf = new PrecompilationFile(path, mainPath, functions);
		precfList.add(precf);

		precf.dependencies.forEach(this::processFile);
	}

}
