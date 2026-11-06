import * as grpc from "@grpc/grpc-js";
import { Err, Ok, type AsyncResult, type Result } from "results-ts";

export type CallError =
    | { readonly kind: "grpc"; readonly status: string; readonly details: string }
    | { readonly kind: "invalid_input"; readonly message: string };

type UnaryMethod<Request, Response> = (
    request: Request,
    callback: grpc.requestCallback<Response>,
) => grpc.ClientUnaryCall;

// Inference on the generated overloads picks the last one, which is (request, callback).
type RequestOf<Method> = Method extends UnaryMethod<infer Request, infer _> ? Request : never;
type ResponseOf<Method> = Method extends UnaryMethod<infer _, infer Response> ? Response : never;

// Generated clients also have a string index signature, which must not count as a method name.
type UnaryName<Client> = keyof {
    [
        Name in keyof Client as string extends Name
            ? never
            : Client[Name] extends UnaryMethod<infer _Request, infer _Response>
              ? Name
              : never
    ]: true;
};

export function call<Client extends object, Name extends UnaryName<Client>>(
    client: Client,
    name: Name,
    request: RequestOf<Client[Name]>,
): AsyncResult<ResponseOf<Client[Name]>, CallError> {
    type Response = ResponseOf<Client[Name]>;
    const method = client[name] as UnaryMethod<RequestOf<Client[Name]>, Response>;

    return Ok(request)
        .mapAsync(
            (value) =>
                new Promise<UnaryOutcome<Response>>((resolve) => {
                    method.call(client, value, (error, response) => resolve({ error, response }));
                }),
        )
        .andThen(toResult);
}

interface UnaryOutcome<Response> {
    readonly error: grpc.ServiceError | null;
    readonly response: Response | undefined;
}

function toResult<Response>(outcome: UnaryOutcome<Response>): Result<Response, CallError> {
    if (outcome.error) return Err(fromServiceError(outcome.error));
    if (outcome.response === undefined) return Err(emptyResponse());

    return Ok(outcome.response);
}

// grpc-js reports requests it cannot encode as INTERNAL, but they are the caller's mistake.
const SERIALIZATION_FAILURE = "Request message serialization failure";

export function fromServiceError(error: grpc.ServiceError): CallError {
    if (error.details.startsWith(SERIALIZATION_FAILURE)) {
        return { kind: "invalid_input", message: error.details };
    }
    return { kind: "grpc", status: grpc.status[error.code] ?? "UNKNOWN", details: error.details };
}

function emptyResponse(): CallError {
    return { kind: "grpc", status: "INTERNAL", details: "The server sent no response" };
}
