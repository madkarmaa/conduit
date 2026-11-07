use std::path::PathBuf;

use prost::Message;

const PROTO_ROOT: &str = "../../src/main/proto";

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let protos: Vec<PathBuf> = std::fs::read_dir(format!("{PROTO_ROOT}/conduit/v1"))?
        .map(|entry| entry.map(|entry| entry.path()))
        .collect::<Result<_, _>>()?;

    // protox compiles the protos in Rust, so a build needs no protoc for any target.
    let descriptors = protox::compile(&protos, [PROTO_ROOT])?;
    let descriptor_bytes = descriptors.encode_to_vec();

    // The well-known types come from pbjson-types, which can turn them into JSON too.
    tonic_prost_build::configure()
        .build_server(false)
        .compile_well_known_types(true)
        .extern_path(".google.protobuf", "::pbjson_types")
        .compile_fds(descriptors)?;

    // Requests and responses convert to and from the canonical protobuf JSON, with camelCase
    // fields, so tools can pass JSON through and show results as JSON. Default values stay in,
    // since a missing 0 or false reads as unknown.
    pbjson_build::Builder::new()
        .register_descriptors(&descriptor_bytes)?
        .emit_fields()
        .build(&[".conduit"])?;

    println!("cargo:rerun-if-changed={PROTO_ROOT}");
    Ok(())
}
