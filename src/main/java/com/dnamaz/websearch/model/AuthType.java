package com.dnamaz.websearch.model;

/**
 * Categorizes how a provider authenticates with its backing service.
 */
public enum AuthType {
    /** Local providers (ONNX, Lucene) -- no auth needed */
    NONE,
    /** Simple API key in Authorization header (OpenAI, Cohere, Voyage) */
    API_KEY,
    /** AWS credential chain -- IAM, STS, instance profile, SSO (Bedrock) */
    AWS_CREDENTIALS,
    /** Azure API key or Entra ID token (Azure OpenAI) */
    AZURE_IDENTITY,
    /** Google Application Default Credentials (Vertex AI) */
    GCP_ADC
}
