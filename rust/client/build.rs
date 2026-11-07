use std::env;
use std::path::PathBuf;

const PROTO_ROOT: &str = "../../src/main/proto";

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let protos: Vec<PathBuf> = std::fs::read_dir(format!("{PROTO_ROOT}/conduit/v1"))?
        .map(|entry| entry.map(|entry| entry.path()))
        .collect::<Result<_, _>>()?;
    let descriptors = PathBuf::from(env::var("OUT_DIR")?).join("conduit_descriptor.bin");

    // The well-known types come from pbjson-types, which can turn them into JSON too.
    tonic_prost_build::configure()
        .build_server(false)
        .file_descriptor_set_path(&descriptors)
        .compile_well_known_types(true)
        .extern_path(".google.protobuf", "::pbjson_types")
        .compile_protos(&protos, &[PathBuf::from(PROTO_ROOT)])?;

    // Requests and responses convert to and from the canonical protobuf JSON, with camelCase
    // fields, so tools can pass JSON through and show results as JSON. Default values stay in,
    // since a missing 0 or false reads as unknown.
    pbjson_build::Builder::new()
        .register_descriptors(&std::fs::read(&descriptors)?)?
        .emit_fields()
        .build(&[".conduit"])?;

    println!("cargo:rerun-if-changed={PROTO_ROOT}");
    Ok(())
}
