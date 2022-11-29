import * as vscode from "vscode";


import * as net from 'net';

import {LanguageClientOptions, RevealOutputChannelOn,} from "vscode-languageclient";

import {LanguageClient, ServerOptions, State, StreamInfo,} from "vscode-languageclient/node";
import path = require("path");
import fs = require("fs");
import os = require('node:os');

const outputChannel = vscode.window.createOutputChannel("IdeaLS Client");

export class IdealsClient {
  private languageClient?: LanguageClient;
  private context?: vscode.ExtensionContext;

  setContext(context: vscode.ExtensionContext) {
    this.context = context;
  }

  async init(): Promise<void> {
    try {
      //Server options. LS client will use these options to start the LS.
      let serverOptions: ServerOptions = this.getServerOptions();

      //creating the language client.
      let clientId = "ideals-client";
      let clientName = "IdeaLS Client";
      let clientOptions: LanguageClientOptions = {
        documentSelector: [{pattern: "**/*" /* scheme: "file", language: "java" */}],
        outputChannel: outputChannel,
        revealOutputChannelOn: RevealOutputChannelOn.Never,
      };
      this.languageClient = new LanguageClient(
          clientId,
          clientName,
          serverOptions,
          clientOptions
      );

      const disposeDidChange = this.languageClient.onDidChangeState(
          (stateChangeEvent) => {
            if (stateChangeEvent.newState === State.Stopped) {
              vscode.window.showErrorMessage(
                  "Failed to initialize the extension"
              );
            } else if (stateChangeEvent.newState === State.Running) {
              vscode.window.showInformationMessage(
                  "Extension initialized successfully!"
              );
            }
          }
      );

      this.languageClient.start().then(() => {
        disposeDidChange.dispose();
      });

    } catch (exception) {
      return Promise.reject("Extension error!");
    }
  }

  //Create a command to be run to start the LS java process.
  getServerOptions() {
    let transportEnv : string | undefined = vscode.workspace.getConfiguration('ideals').get('startup.transport');

    if (transportEnv?.toLowerCase() === "tcp") {
      // Connect to language server via socket
      let portEnv = process.env.IDEALS_TCP_PORT;

      let connectionInfo = {
        port: +(portEnv || 8989)
      };

      return () => {
        try {
          let socket = net.connect(connectionInfo);
          let result: StreamInfo = {
            writer: socket,
            reader: socket
          };
          return Promise.resolve(result);

        } catch (exception) {
          console.log("failed to connect: " + exception);
          throw exception;
        }
      };
    }

    let ideaExecutablePath : string | undefined = vscode.workspace.getConfiguration('ideals').get('startup.ideaExecutablePath');

    if (!ideaExecutablePath) {
      throw new Error("Path to IntelliJ IDEA executable must be specified in environment variable IDEALS_IJ_PATH");
    }
    let isToolBox = vscode.workspace.getConfiguration('ideals').get('startup.isIdeaInstalledViaToolBox');
    var content : string;
    var vmoptionsPath : string; 
    if (isToolBox) {
      var ideaVersionDir = path.normalize(path.dirname(path.dirname(ideaExecutablePath)));
      var ideaVersion = path.basename(ideaVersionDir);
      var dirWithVmOptions = path.dirname(ideaVersionDir);
      vmoptionsPath = path.join(dirWithVmOptions, path.basename(ideaVersion) + ".vmoptions");
    } else {
      var parent = path.normalize(path.dirname(ideaExecutablePath));
      vmoptionsPath = path.join(parent, path.basename(ideaExecutablePath) + ".vmoptions");
    }
    content = fs.readFileSync(vmoptionsPath).toString();
    content += "\n-Djava.awt.headless=true"; 

    const tmpdir = os.tmpdir();
    const finalPath = path.join(tmpdir, String(process.pid) + ".vmoptions");
    fs.writeFileSync(finalPath, content);
    

    let serverOptions: ServerOptions = {
      command: ideaExecutablePath,
      args: ["lsp-server"],
      options: {
        env: {
          IDEA_VM_OPTIONS: finalPath,
        }
      },
    };
    return serverOptions;

  }
}

export const lspClient = new IdealsClient();