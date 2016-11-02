/*
 * Copyright (c) 2010-2016 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.util;

import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

import java.io.*;

/**
 * @author mederly
 */
public class SystemUtil {

	private static final Trace LOGGER = TraceManager.getTrace(SystemUtil.class);

	public static void executeCommand(String command, String input, StringBuilder output) throws IOException {
		LOGGER.debug("Executing {}", command);
		try {
			Process process = Runtime.getRuntime().exec(command);

			Writer writer = new OutputStreamWriter(process.getOutputStream());
			writer.append(input);
			writer.close();

			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line = "";
			while ((line = reader.readLine()) != null) {
				output.append(line).append("\n");
			}
			try {
				process.waitFor();
			} catch (InterruptedException e) {
				throw new SystemException("Got interruptedException while waiting for external command execution", e);
			}
		} catch (IOException e) {
			LoggingUtils.logUnexpectedException(LOGGER, "Couldn't execute command {}", e, command);
			throw e;
		}
		LOGGER.debug("Finished executing {}; result has a length of {} characters", command, output.length());
	}
}
