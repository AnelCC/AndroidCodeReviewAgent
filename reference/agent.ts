import OpenAI from "openai";
import { readFile, readdir } from "node:fs/promises";
import { resolve, relative, sep } from "node:path";

const client = new OpenAI();
const root = resolve("sample-src");
const crash = await readFile("crash.json", "utf8");
const tools: OpenAI.Responses.Tool[] = [
  {type:"function",name:"list_files",description:"List available Kotlin source files",parameters:{type:"object",properties:{},required:[],additionalProperties:false},strict:true},
  {type:"function",name:"read_source",description:"Read a Kotlin source file from the sample-src folder",parameters:{type:"object",properties:{file:{type:"string"}},required:["file"],additionalProperties:false},strict:true}
];
let input: any[] = [{role:"user",content:`Investigate this crash, use tools to inspect code, and recommend a safe fix and a regression test. Do not modify files. Crash: ${crash}`}];
for (let step=0;step<6;step++) {
  const response = await client.responses.create({model:process.env.OPENAI_MODEL ?? "gpt-5.1",instructions:"You are a mobile crash investigator. Inspect source before making claims. Never claim to have run tests. Return root cause, evidence, suggested fix, regression test, and uncertainty.",tools,input});
  input.push(...response.output);
  const calls = response.output.filter((item):item is Extract<typeof item,{type:"function_call"}> => item.type === "function_call");
  if (!calls.length) { console.log(response.output_text); break; }
  for (const call of calls) {
    let output: string;
    try {
      if(call.name==="list_files") output=JSON.stringify((await readdir(root)).filter(f=>f.endsWith(".kt")));
      else if(call.name==="read_source") {
        const {file}=JSON.parse(call.arguments);
        const path=resolve(root,file);
        if(!path.startsWith(root+sep) || !path.endsWith(".kt") || relative(root,path).startsWith("..")) throw Error("File outside allowed folder");
        output=await readFile(path,"utf8");
      } else output="Unknown tool";
    } catch(e) {output=String(e)}
    input.push({type:"function_call_output",call_id:call.call_id,output});
  }
}
